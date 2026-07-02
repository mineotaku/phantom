import json
from http.server import BaseHTTPRequestHandler, HTTPServer
import urllib.parse
import os
import random
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart

# In-memory storage for active directory
users = {}  # username -> { name, publicKey, deviceId, isOnline }
messages = {}  # username -> list of chat message dicts
otp_store = {}  # email -> otp_code

# SMTP Email Configuration for automated dispatches
SMTP_EMAIL = "mineotaku69@gmail.com"
# To authenticate with Gmail, generate an App Password in Google Account Settings:
# Google Account -> Security -> 2-Step Verification -> App Passwords
SMTP_PASSWORD = os.environ.get("SMTP_PASSWORD", "")

class PhantomServerHandler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

    def do_GET(self):
        parsed_url = urllib.parse.urlparse(self.path)
        path = parsed_url.path
        query = urllib.parse.parse_qs(parsed_url.query)

        if path == "/api/users":
            self.send_json_response(200, list(users.values()))
        elif path == "/api/messages/poll":
            user = query.get("user", [None])[0]
            if not user:
                self.send_json_response(400, {"error": "Missing 'user' query parameter"})
                return
            user_msgs = messages.pop(user, [])
            self.send_json_response(200, user_msgs)
        else:
            self.send_json_response(404, {"error": "Endpoint not found"})

    def do_POST(self):
        content_length = int(self.headers['Content-Length'] or 0)
        body = self.rfile.read(content_length).decode('utf-8')
        try:
            data = json.loads(body) if body else {}
        except Exception:
            self.send_json_response(400, {"error": "Invalid JSON"})
            return

        if self.path == "/api/otp/request":
            email = data.get("email")
            if not email:
                self.send_json_response(400, {"error": "Missing 'email'"})
                return
            
            # Generate 6-digit secure code
            otp_code = str(random.randint(100000, 999999))
            otp_store[email] = otp_code
            print(f"[*] OTP Code generated for {email}: {otp_code}")

            success = False
            error_msg = ""

            if SMTP_PASSWORD:
                try:
                    # Construct SMTP MIME package
                    msg = MIMEMultipart()
                    msg['From'] = SMTP_EMAIL
                    msg['To'] = email
                    msg['Subject'] = "🔒 Phantom Secure Verification Token"

                    body_content = f"""
Hello,

Your secure Phantom identity verification token code is: {otp_code}

Please enter this token in the console prompt to verify your session.

Regards,
Phantom Security Engine
                    """.strip()
                    msg.attach(MIMEText(body_content, 'plain'))

                    server = smtplib.SMTP('smtp.gmail.com', 587)
                    server.starttls()
                    server.login(SMTP_EMAIL, SMTP_PASSWORD)
                    server.sendmail(SMTP_EMAIL, email, msg.as_string())
                    server.quit()
                    success = True
                    print(f"[+] Real SMTP email successfully routed to {email} via {SMTP_EMAIL}.")
                except Exception as e:
                    error_msg = str(e)
                    print(f"[-] SMTP Dispatch Error: {error_msg}")
            else:
                print("[-] SMTP_PASSWORD not set. Utilizing fallback local display loop.")
                error_msg = "Real SMTP credentials not configured on backend."

            self.send_json_response(200, {
                "status": "success" if (success or not SMTP_PASSWORD) else "failed",
                "otp": otp_code if not success else None, # Fallback code returned if smtp is not set
                "error": error_msg if not success else None
            })

        elif self.path == "/api/otp/verify":
            email = data.get("email")
            code = data.get("code")
            if not email or not code:
                self.send_json_response(400, {"error": "Missing 'email' or 'code'"})
                return
            
            expected = otp_store.get(email)
            if expected and expected == code:
                self.send_json_response(200, {"status": "success", "message": "OTP verified successfully"})
                print(f"[+] Verified session for {email}")
            else:
                self.send_json_response(400, {"status": "failed", "error": "Invalid verification token code"})

        elif self.path == "/api/users/register":
            name = data.get("name")
            pub_key = data.get("publicKey")
            dev_id = data.get("deviceId")
            if not name or not pub_key:
                self.send_json_response(400, {"error": "Missing 'name' or 'publicKey'"})
                return
            users[name] = {
                "name": name,
                "publicKey": pub_key,
                "deviceId": dev_id or "unknown",
                "isOnline": True
            }
            print(f"[*] Registered User: {name} | Key: {pub_key}")
            self.send_json_response(200, {"status": "success", "message": f"User {name} registered"})

        elif self.path == "/api/messages/send":
            sender = data.get("sender")
            recipient = data.get("recipient")
            ciphertext = data.get("ciphertext")
            mac = data.get("mac")
            timestamp = data.get("timestamp")

            if not sender or not recipient or not ciphertext:
                self.send_json_response(400, {"error": "Missing payload params"})
                return

            msg_payload = {
                "id": data.get("id", "msg_gen"),
                "sender": sender,
                "text": data.get("text", "[Encrypted Payload]"),
                "ciphertext": ciphertext,
                "mac": mac or "",
                "timestamp": timestamp or "Now",
                "isEncrypted": True,
                "isDelivered": True,
                "isRead": False
            }

            if recipient not in messages:
                messages[recipient] = []
            messages[recipient].append(msg_payload)
            print(f"[+] Message: {sender} -> {recipient} | Ciphertext: {ciphertext[:30]}...")
            self.send_json_response(200, {"status": "success", "message": "Message relayed"})
        else:
            self.send_json_response(404, {"error": "Endpoint not found"})

    def send_json_response(self, code, data):
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode('utf-8'))

def run(port=8080):
    server_address = ('', port)
    httpd = HTTPServer(server_address, PhantomServerHandler)
    print(f"==================================================")
    print(f" PHANTOM E2EE SECURE MESSAGING SERVER            ")
    print(f" Running on http://localhost:{port}              ")
    print(f" Press Ctrl+C to terminate                       ")
    print(f"==================================================")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    print("\n[-] Shutting down server...")

if __name__ == '__main__':
    port = int(os.environ.get("PORT", 8080))
    run(port)
