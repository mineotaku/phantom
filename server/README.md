# Phantom Relay API

Production-oriented FastAPI relay for the Phantom Android app.

## Run Locally

```powershell
cd server
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:EXPOSE_DEV_OTP="true"
uvicorn server:app --host 127.0.0.1 --port 8080 --reload
```

For local smoke tests, install `requirements-dev.txt`.

The default database is SQLite at `server/phantom.db`. For production, set
`DATABASE_URL` to Postgres, for example:

```text
DATABASE_URL=postgresql+psycopg://user:password@db-host:5432/phantom
```

## Environment

- `DATABASE_URL`: SQLAlchemy database URL. Defaults to `sqlite:///./phantom.db`.
- `SMTP_HOST`: SMTP host. Defaults to `smtp.gmail.com`.
- `SMTP_PORT`: SMTP port. Defaults to `587`.
- `SMTP_EMAIL`: sender email.
- `SMTP_PASSWORD`: SMTP app password.
- `OTP_TTL_SECONDS`: OTP validity window. Defaults to `300`.
- `EXPOSE_DEV_OTP`: returns OTPs in API responses when SMTP fails. Use only locally.
- `CORS_ALLOWED_ORIGIN`: optional browser origin allow-list.
- `HOST`: local host for `python server.py`. Defaults to `127.0.0.1`.
- `PORT`: local port. Defaults to `8080`.

## Android-Compatible Routes

- `POST /api/otp/request`
- `POST /api/otp/verify`
- `POST /api/users/register`
- `GET /api/users`
- `POST /api/messages/send`
- `GET /api/messages/poll?user=<name>`
- `GET /health`
