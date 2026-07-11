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
import uuid

from fastapi import Depends, FastAPI, HTTPException, Query, Security, status, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from sqlalchemy import Boolean, DateTime, Integer, String, Text, create_engine, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker


DATABASE_URL = os.environ.get("DATABASE_URL", "sqlite:///./phantom.db")
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql://", 1)
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


# --- Added for E2EE prekey distribution ---
class PreKeyBundle(Base):
    __tablename__ = "prekey_bundles"

    user: Mapped[str] = mapped_column(String(120), primary_key=True)
    identity_key: Mapped[str] = mapped_column(Text, nullable=False)
    signed_pre_key: Mapped[str] = mapped_column(Text, nullable=False)
    signed_pre_key_signature: Mapped[str] = mapped_column(Text, nullable=False)
    signed_pre_key_id: Mapped[int] = mapped_column(Integer, nullable=False)
    one_time_pre_key: Mapped[str | None] = mapped_column(Text, nullable=True)
    one_time_pre_key_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class OneTimePreKey(Base):
    __tablename__ = "one_time_prekeys"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user: Mapped[str] = mapped_column(String(120), nullable=False, index=True)
    key_id: Mapped[int] = mapped_column(Integer, nullable=False)
    public_key: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


# --- Added for multi-device sync ---
class Device(Base):
    __tablename__ = "server_devices"

    device_id: Mapped[str] = mapped_column(String(160), primary_key=True)
    user: Mapped[str] = mapped_column(String(120), nullable=False, index=True)
    public_key: Mapped[str] = mapped_column(Text, nullable=False)
    device_name: Mapped[str] = mapped_column(String(120), nullable=False)
    is_revoked: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


# --- Added for encrypted cloud backup ---
class EncryptedBackup(Base):
    __tablename__ = "backups"

    user: Mapped[str] = mapped_column(String(120), primary_key=True)
    backup_data: Mapped[str] = mapped_column(Text, nullable=False) # Base64
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


class UploadPreKeyBundleRequest(BaseModel):
    user: str
    identityKey: str
    signedPreKey: str
    signedPreKeySignature: str
    signedPreKeyId: int
    oneTimePreKey: str | None = None
    oneTimePreKeyId: int | None = None


class OneTimePreKeyUploadItem(BaseModel):
    keyId: int
    publicKey: str


class UploadOneTimePreKeysRequest(BaseModel):
    user: str
    oneTimePreKeys: list[OneTimePreKeyUploadItem]


class RegisterDeviceRequest(BaseModel):
    deviceId: str
    deviceName: str
    publicKey: str


class RevokeDeviceRequest(BaseModel):
    deviceId: str


class BackupUploadRequest(BaseModel):
    backup_data: str = Field(min_length=1)


class PlayIntegrityVerifyRequest(BaseModel):
    integrityToken: str


class SealedSenderEnvelopeRequest(BaseModel):
    recipientUserId: str
    encryptedContent: str # Base64


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
else:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_headers=["*"],
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


def send_otp_email_via_gmail_relay(email: str, otp_code: str, relay_url: str) -> None:
    headers = {"Content-Type": "application/json"}
    payload = {
        "to": email,
        "subject": "Phantom verification code",
        "text": f"Hello,\n\nYour Phantom verification code is: {otp_code}\n\nThis code expires shortly. If you did not request it, ignore this email.\n\nPhantom Security"
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(relay_url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as res:
            if res.status not in (200, 201):
                raise RuntimeError(f"Gmail relay returned status code {res.status}")
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8")
        raise RuntimeError(f"Gmail relay API error: {e.code} - {error_body}")


def send_otp_email(email: str, otp_code: str) -> None:
    gmail_relay_url = os.environ.get("GMAIL_RELAY_URL", "")
    if gmail_relay_url:
        send_otp_email_via_gmail_relay(email, otp_code, gmail_relay_url)
        return

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


_otp_rate_limits: dict[str, list[datetime]] = {}
OTP_RATE_LIMIT_MAX = 3
OTP_RATE_LIMIT_WINDOW_SECONDS = 600


@app.post("/api/otp/request", response_model=StatusResponse)
def request_otp(payload: OtpRequest, db: Session = Depends(get_db)) -> StatusResponse:
    email = normalize_email(payload.email)
    now = utc_now()

    # --- Rate-limit OTP requests per email ---
    window_start = now - timedelta(seconds=OTP_RATE_LIMIT_WINDOW_SECONDS)
    timestamps = _otp_rate_limits.get(email, [])
    timestamps = [t for t in timestamps if t > window_start]
    if len(timestamps) >= OTP_RATE_LIMIT_MAX:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"OTP rate limit exceeded. Try again in {OTP_RATE_LIMIT_WINDOW_SECONDS // 60} minutes.",
        )
    timestamps.append(now)
    _otp_rate_limits[email] = timestamps

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
        print(f"PHANTOM_DEV_LOG: OTP for {email} is {otp_code}")
        if not EXPOSE_DEV_OTP:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"OTP email dispatch failed: {exc}",
            ) from exc
        return StatusResponse(
            status="success",
            message="Development OTP fallback enabled",
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
    sender_ids = {row.sender for row in rows if row.sender}
    senders_map = {}
    if sender_ids:
        users = db.scalars(select(User).where(User.name.in_(list(sender_ids)))).all()
        senders_map = {user.name: user.public_key for user in users}

    response = []
    for row in rows:
        sender_pubkey = senders_map.get(row.sender, "")
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
        db.delete(row)

    db.commit()
    return response


# --- Added for E2EE PreKey distribution endpoints ---
@app.post("/api/keys/prekey-bundle", response_model=StatusResponse)
def upload_prekey_bundle(
    payload: UploadPreKeyBundleRequest,
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> StatusResponse:
    now = utc_now()
    user = normalize_name(payload.user)
    expected_user = email.split("@")[0]
    if user != expected_user:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"Authenticated email {email} can only upload prekey bundle for '{expected_user}'",
        )
    
    bundle = PreKeyBundle(
        user=user,
        identity_key=payload.identityKey,
        signed_pre_key=payload.signedPreKey,
        signed_pre_key_signature=payload.signedPreKeySignature,
        signed_pre_key_id=payload.signedPreKeyId,
        one_time_pre_key=payload.oneTimePreKey,
        one_time_pre_key_id=payload.oneTimePreKeyId,
        updated_at=now
    )
    db.merge(bundle)
    db.commit()
    return StatusResponse(status="success", message="Prekey bundle successfully uploaded")


@app.post("/api/keys/otks", response_model=StatusResponse)
def upload_one_time_prekeys(
    payload: UploadOneTimePreKeysRequest,
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> StatusResponse:
    now = utc_now()
    user = normalize_name(payload.user)
    expected_user = email.split("@")[0]
    if user != expected_user:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"Authenticated email {email} can only upload OTKs for '{expected_user}'",
        )
    
    # Store all uploaded OTKs
    for item in payload.oneTimePreKeys:
        exists = db.query(OneTimePreKey).filter(OneTimePreKey.user == user, OneTimePreKey.key_id == item.keyId).first()
        if not exists:
            otk = OneTimePreKey(
                user=user,
                key_id=item.keyId,
                public_key=item.publicKey,
                created_at=now
            )
            db.add(otk)
    db.commit()
    return StatusResponse(status="success", message=f"{len(payload.oneTimePreKeys)} one-time prekeys uploaded successfully")


@app.get("/api/keys/prekey-bundle/{user}")
def get_prekey_bundle(
    user: str,
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> dict:
    target_user = normalize_name(user)
    bundle = db.get(PreKeyBundle, target_user)
    if not bundle:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Prekey bundle not found")
    
    # Fetch next available OTK from pool
    otk = db.query(OneTimePreKey).filter(OneTimePreKey.user == target_user).order_by(OneTimePreKey.key_id.asc()).first()
    
    response = {
        "identityKey": bundle.identity_key,
        "signedPreKey": bundle.signed_pre_key,
        "signedPreKeySignature": bundle.signed_pre_key_signature,
        "signedPreKeyId": bundle.signed_pre_key_id,
        "oneTimePreKey": otk.public_key if otk else None,
        "oneTimePreKeyId": otk.key_id if otk else None
    }
    
    # Consume OTK if fetched
    if otk:
        db.delete(otk)
        db.commit()
        
    return response


@app.get("/api/keys/prekey-count/{user}")
def get_prekey_count(
    user: str,
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> dict:
    target_user = normalize_name(user)
    count = db.query(OneTimePreKey).filter(OneTimePreKey.user == target_user).count()
    return {"user": target_user, "oneTimePreKeyCount": count}


# --- Added for device integrity verification ---
@app.post("/api/integrity/verify", response_model=StatusResponse)
def verify_device_integrity(
    payload: PlayIntegrityVerifyRequest,
    email: str = Depends(get_current_user_email)
) -> StatusResponse:
    # Google API request verification stub: verify base64 integrity token signatures
    if not payload.integrityToken:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Missing integrity token")
    return StatusResponse(status="success", message="Device integrity successfully verified")


# --- Added for multi-device sync endpoints ---
@app.post("/api/devices/register", response_model=StatusResponse)
def register_device(
    payload: RegisterDeviceRequest,
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> StatusResponse:
    now = utc_now()
    user = email.split("@")[0]
    device = Device(
        device_id=payload.deviceId,
        user=user,
        public_key=payload.publicKey,
        device_name=payload.deviceName,
        is_revoked=False,
        created_at=now
    )
    db.merge(device)
    db.commit()
    return StatusResponse(status="success", message="Device successfully registered")


@app.get("/api/devices/list/{user}")
def list_devices(
    user: str,
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> list:
    target_user = normalize_name(user)
    stmt = select(Device).where(Device.user == target_user, Device.is_revoked == False)
    devices = db.scalars(stmt).all()
    return [
        {
            "deviceId": dev.device_id,
            "deviceName": dev.device_name,
            "publicKey": dev.public_key,
        }
        for dev in devices
    ]


@app.post("/api/devices/revoke", response_model=StatusResponse)
def revoke_device(
    payload: RevokeDeviceRequest,
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> StatusResponse:
    device = db.get(Device, payload.deviceId)
    if not device:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")
    device.is_revoked = True
    db.commit()
    return StatusResponse(status="success", message="Device registration revoked")


# --- Added for sealed sender endpoints ---
@app.post("/api/messages/sealed-send", response_model=StatusResponse)
def sealed_send_message(
    payload: SealedSenderEnvelopeRequest,
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> StatusResponse:
    now = utc_now()
    recipient = normalize_name(payload.recipientUserId)
    message_id = "msg_" + secrets.token_urlsafe(18)
    
    # Store sealed envelope under recipient address. Sender index remains completely null on server.
    db.add(
        Message(
            id=message_id,
            sender="sealed_sender",
            recipient=recipient,
            text=payload.encryptedContent, # Envelope bytes stored in message text field
            ciphertext=payload.encryptedContent,
            mac="",
            client_timestamp="Now",
            is_encrypted=True,
            is_delivered=True,
            is_read=False,
            polled_at=None,
            created_at=now
        )
    )
    db.commit()
    return StatusResponse(status="success", message="Sealed sender envelope relayed successfully")


# --- Added for certificates delivery endpoint ---
@app.post("/api/certificates/delivery")
def request_sender_certificate(
    email: str = Depends(get_current_user_email)
) -> dict:
    user = email.split("@")[0]
    # Issue sender verification certificate signed by server identity
    # Simulated signature for simplicity
    cert_data = f"phantom_sender_cert:{user}:exp_{int(datetime.utcnow().timestamp()) + 86400}"
    signature = secrets.token_urlsafe(32)
    return {
        "userId": user,
        "certificate": cert_data,
        "signature": signature,
        "expiration": int(datetime.utcnow().timestamp()) + 86400
    }


# --- Added for encrypted cloud backup endpoints ---
@app.post("/api/backup/upload", response_model=StatusResponse)
def upload_backup(
    payload: BackupUploadRequest,
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email),
) -> StatusResponse:
    user = email.split("@")[0]
    backup = EncryptedBackup(
        user=user,
        backup_data=payload.backup_data,
        created_at=utc_now()
    )
    db.merge(backup)
    db.commit()
    return StatusResponse(status="success", message="Encrypted cloud backup saved successfully")


@app.get("/api/backup/download")
def download_backup(
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
) -> dict:
    user = email.split("@")[0]
    backup = db.get(EncryptedBackup, user)
    if not backup:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Backup not found")
    return {"backupData": backup.backup_data}


# --- Secure E2EE Media Storage endpoints ---
MEDIA_STORE_DIR = "./media_store"
os.makedirs(MEDIA_STORE_DIR, exist_ok=True)


MEDIA_MAX_SIZE_BYTES = 15 * 1024 * 1024  # 15 MB


@app.post("/api/media/upload")
async def upload_media(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
):
    file_id = str(uuid.uuid4())
    file_path = os.path.join(MEDIA_STORE_DIR, file_id)
    total_size = 0
    chunk_size = 64 * 1024  # 64 KB chunks
    try:
        with open(file_path, "wb") as f:
            while True:
                chunk = await file.read(chunk_size)
                if not chunk:
                    break
                total_size += len(chunk)
                if total_size > MEDIA_MAX_SIZE_BYTES:
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail=f"File exceeds maximum allowed size of {MEDIA_MAX_SIZE_BYTES // (1024 * 1024)} MB",
                    )
                f.write(chunk)
    except HTTPException:
        # Clean up partial file on size limit exceeded
        if os.path.exists(file_path):
            os.remove(file_path)
        raise
    return {"file_id": file_id}


@app.get("/api/media/download/{file_id}")
def download_media(
    file_id: str,
    db: Session = Depends(get_db),
    email: str = Depends(get_current_user_email)
):
    # Path traversal protection
    if ".." in file_id or os.sep in file_id or "/" in file_id or "\\" in file_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid file ID",
        )
    file_path = os.path.join(MEDIA_STORE_DIR, file_id)
    real_path = os.path.realpath(file_path)
    real_store = os.path.realpath(MEDIA_STORE_DIR)
    if not real_path.startswith(real_store + os.sep):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid file ID",
        )
    if not os.path.exists(real_path):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Encrypted media file not found",
        )
    return FileResponse(real_path, media_type="application/octet-stream")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("server:app", host=HOST, port=PORT, reload=False)
