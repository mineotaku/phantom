import os
import re
import secrets
import smtplib
from contextlib import contextmanager
from datetime import datetime, timedelta
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from typing import Generator
import urllib.request
import urllib.error
import json

from fastapi import Depends, FastAPI, HTTPException, Query, Security, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field
from sqlalchemy import Boolean, DateTime, Integer, String, Text, create_engine, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker


DATABASE_URL = os.environ.get("DATABASE_URL", "sqlite:///./phantom.db")
HOST = os.environ.get("HOST", "0.0.0.0")
PORT = int(os.environ.get("PORT", "8080"))
CORS_ALLOWED_ORIGIN = os.environ.get("CORS_ALLOWED_ORIGIN", "")
OTP_TTL_SECONDS = int(os.environ.get("OTP_TTL_SECONDS", "300"))
EXPOSE_DEV_OTP = os.environ.get("EXPOSE_DEV_OTP", "").lower() == "true"

SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.environ.get("SMTP_PORT", "587"))
SMTP_EMAIL = os.environ.get("SMTP_EMAIL", "")
SMTP_PASSWORD = os.environ.get("SMTP_PASSWORD", "")

connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}
engine = create_engine(DATABASE_URL, pool_pre_ping=True, connect_args=connect_args)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"

    name: Mapped[str] = mapped_column(String(120), primary_key=True)
    public_key: Mapped[str] = mapped_column(Text, nullable=False)
    device_id: Mapped[str] = mapped_column(String(160), nullable=False, default="unknown")
    is_online: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class OtpCode(Base):
    __tablename__ = "otp_codes"

    email: Mapped[str] = mapped_column(String(320), primary_key=True)
    code: Mapped[str] = mapped_column(String(6), nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    attempts: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class SessionToken(Base):
    __tablename__ = "sessions"

    token: Mapped[str] = mapped_column(String(96), primary_key=True)
    email: Mapped[str] = mapped_column(String(320), nullable=False, index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class Message(Base):
    __tablename__ = "messages"

    id: Mapped[str] = mapped_column(String(120), primary_key=True)
    sender: Mapped[str] = mapped_column(String(120), nullable=False, index=True)
    recipient: Mapped[str] = mapped_column(String(120), nullable=False, index=True)
    text: Mapped[str] = mapped_column(Text, nullable=False, default="[Encrypted Payload]")
    ciphertext: Mapped[str] = mapped_column(Text, nullable=False)
    mac: Mapped[str] = mapped_column(Text, nullable=False, default="")
    client_timestamp: Mapped[str] = mapped_column(String(80), nullable=False, default="Now")
    is_encrypted: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    is_delivered: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    is_read: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    polled_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class OtpRequest(BaseModel):
    email: str = Field(min_length=3, max_length=320)


class OtpVerifyRequest(BaseModel):
    email: str = Field(min_length=3, max_length=320)
    code: str = Field(min_length=6, max_length=6)


class RegisterUserRequest(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    publicKey: str = Field(min_length=1)
    deviceId: str | None = None


class SendMessageRequest(BaseModel):
    id: str | None = None
    sender: str = Field(min_length=1, max_length=120)
    recipient: str = Field(min_length=1, max_length=120)
    text: str | None = None
    ciphertext: str = Field(min_length=1)
    mac: str | None = None
    timestamp: str | None = None


class StatusResponse(BaseModel):
    status: str
    message: str | None = None
    error: str | None = None
    otp: str | None = None
    sessionToken: str | None = None


app = FastAPI(title="Phantom Relay API", version="1.0.0")

if CORS_ALLOWED_ORIGIN:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=[CORS_ALLOWED_ORIGIN],
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["Content-Type", "Authorization"],
    )


def utc_now() -> datetime:
    return datetime.utcnow()


def normalize_email(email: str) -> str:
    email = email.strip().lower()
    if not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", email):
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Invalid email")
    return email


def normalize_name(name: str) -> str:
    clean = name.strip()
    if not clean:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Invalid name")
    return clean


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


security = HTTPBearer()


def get_current_user_email(
    credentials: HTTPAuthorizationCredentials = Security(security),
    db: Session = Depends(get_db)
) -> str:
    token = credentials.credentials
    now = datetime.utcnow()
    stmt = select(SessionToken).where(SessionToken.token == token, SessionToken.expires_at > now)
    session = db.scalar(stmt)
    if not session:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired session token",
        )
    return session.email


@contextmanager
def smtp_connection():
    server = smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15)
    try:
        server.starttls()
        server.login(SMTP_EMAIL, SMTP_PASSWORD)
        yield server
    finally:
        server.quit()


def send_otp_email_via_resend(email: str, otp_code: str, api_key: str) -> None:
    url = "https://api.resend.com/emails"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    payload = {
        "from": "Phantom Security <onboarding@resend.dev>",
        "to": [email],
        "subject": "Phantom verification code",
        "html": f"<p>Hello,</p><p>Your Phantom verification code is: <strong>{otp_code}</strong></p><p>This code expires shortly. If you did not request it, ignore this email.</p><p>Phantom Security</p>"
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as res:
            if res.status not in (200, 201):
                raise RuntimeError(f"Resend returned status code {res.status}")
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8")
        raise RuntimeError(f"Resend API error: {e.code} - {error_body}")


def send_otp_email(email: str, otp_code: str) -> None:
    resend_api_key = os.environ.get("RESEND_API_KEY", "")
    if resend_api_key:
        send_otp_email_via_resend(email, otp_code, resend_api_key)
        return

    if not SMTP_EMAIL or not SMTP_PASSWORD:
        raise RuntimeError("SMTP_EMAIL/SMTP_PASSWORD are not configured")

    msg = MIMEMultipart()
    msg["From"] = SMTP_EMAIL
    msg["To"] = email
    msg["Subject"] = "Phantom verification code"
    body = (
        "Hello,\n\n"
        f"Your Phantom verification code is: {otp_code}\n\n"
        "This code expires shortly. If you did not request it, ignore this email.\n\n"
        "Phantom Security"
    )
    msg.attach(MIMEText(body, "plain"))

    with smtp_connection() as server:
        server.sendmail(SMTP_EMAIL, email, msg.as_string())


@app.on_event("startup")
def create_tables() -> None:
    Base.metadata.create_all(bind=engine)


@app.get("/health")
def health(db: Session = Depends(get_db)) -> dict[str, str]:
    db.execute(select(1))
    return {"status": "ok"}


@app.post("/api/otp/request", response_model=StatusResponse)
def request_otp(payload: OtpRequest, db: Session = Depends(get_db)) -> StatusResponse:
    email = normalize_email(payload.email)
    now = utc_now()
    otp_code = f"{secrets.randbelow(900000) + 100000}"

    db.merge(
        OtpCode(
            email=email,
            code=otp_code,
            expires_at=now + timedelta(seconds=OTP_TTL_SECONDS),
            attempts=0,
            created_at=now,
        )
    )
    db.commit()

    try:
        send_otp_email(email, otp_code)
    except Exception as exc:
        if not EXPOSE_DEV_OTP:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"OTP email dispatch failed: {exc}",
            ) from exc
        return StatusResponse(
            status="success",
            message="Development OTP fallback enabled",
            otp=otp_code,
            error=str(exc),
        )

    return StatusResponse(status="success", message="OTP sent")


@app.post("/api/otp/verify", response_model=StatusResponse)
def verify_otp(payload: OtpVerifyRequest, db: Session = Depends(get_db)) -> StatusResponse:
    email = normalize_email(payload.email)
    code = payload.code.strip()
    now = utc_now()
    entry = db.get(OtpCode, email)

    if entry is None or entry.expires_at < now:
        if entry is not None:
            db.delete(entry)
            db.commit()
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired code")

    entry.attempts += 1
    if entry.attempts > 5:
        db.delete(entry)
        db.commit()
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="Too many attempts")

    if not secrets.compare_digest(entry.code, code):
        db.commit()
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired code")

    db.delete(entry)
    token = "ps_" + secrets.token_urlsafe(48)
    db.add(
        SessionToken(
            token=token,
            email=email,
            expires_at=now + timedelta(days=30),
            created_at=now,
        )
    )
    db.commit()
    return StatusResponse(status="success", message="OTP verified successfully", sessionToken=token)


@app.get("/api/users")
def list_users(
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> list[dict[str, object]]:
    users = db.scalars(select(User).order_by(User.name)).all()
    return [
        {
            "name": user.name,
            "publicKey": user.public_key,
            "deviceId": user.device_id,
            "isOnline": user.is_online,
        }
        for user in users
    ]


@app.post("/api/users/register", response_model=StatusResponse)
def register_user(
    payload: RegisterUserRequest,
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> StatusResponse:
    now = utc_now()
    name = normalize_name(payload.name)
    expected_name = email.split("@")[0]
    if name != expected_name:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"Authenticated email {email} can only register user '{expected_name}'",
        )
    user = User(
        name=name,
        public_key=payload.publicKey,
        device_id=payload.deviceId or "unknown",
        is_online=True,
        created_at=now,
        updated_at=now,
    )
    existing = db.get(User, name)
    if existing is not None:
        user.created_at = existing.created_at
    db.merge(user)
    db.commit()
    return StatusResponse(status="success", message=f"User {name} registered")


@app.post("/api/messages/send", response_model=StatusResponse)
def send_message(
    payload: SendMessageRequest,
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> StatusResponse:
    now = utc_now()
    sender = normalize_name(payload.sender)
    recipient = normalize_name(payload.recipient)
    expected_sender = email.split("@")[0]
    if sender != expected_sender:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Sender name must match authenticated user name",
        )
    message_id = payload.id or "msg_" + secrets.token_urlsafe(18)

    db.merge(
        Message(
            id=message_id,
            sender=sender,
            recipient=recipient,
            text=payload.text or "[Encrypted Payload]",
            ciphertext=payload.ciphertext,
            mac=payload.mac or "",
            client_timestamp=payload.timestamp or "Now",
            is_encrypted=True,
            is_delivered=True,
            is_read=False,
            polled_at=None,
            created_at=now,
        )
    )
    db.commit()
    return StatusResponse(status="success", message="Message relayed")


@app.get("/api/messages/poll")
def poll_messages(
    user: str = Query(min_length=1, max_length=120),
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> list[dict[str, object]]:
    recipient = normalize_name(user)
    expected_recipient = email.split("@")[0]
    if recipient != expected_recipient:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You can only poll messages for your own account",
        )
    rows = db.scalars(
        select(Message)
        .where(Message.recipient == recipient, Message.polled_at.is_(None))
        .order_by(Message.created_at)
        .limit(100)
    ).all()

    now = utc_now()
    response = []
    for row in rows:
        row.polled_at = now
        sender_user = db.get(User, row.sender)
        sender_pubkey = sender_user.public_key if sender_user else ""
        response.append(
            {
                "id": row.id,
                "sender": row.sender,
                "senderPublicKey": sender_pubkey,
                "text": row.text,
                "ciphertext": row.ciphertext,
                "mac": row.mac,
                "timestamp": row.client_timestamp,
                "isEncrypted": row.is_encrypted,
                "isDelivered": row.is_delivered,
                "isRead": row.is_read,
            }
        )

    db.commit()
    return response


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("server:app", host=HOST, port=PORT, reload=False)
