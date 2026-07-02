<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Phantom — Privacy-First Secure E2EE Messenger

Phantom is an Android messaging application that demonstrates a full end-to-end encryption workflow inspired by the Signal Protocol. It features a real-time cryptographic pipeline visualization, local Room database persistence, OTP-based email authentication via FormSubmit, and a multi-tab security console.

## Features

- **Email OTP Authentication** — Real email-based one-time password delivery via FormSubmit with SMTP relay simulation
- **E2EE Message Pipeline** — Visual step-by-step encryption simulation (AES-256-GCM, HMAC-SHA256, Double Ratchet)
- **Local Room Database** — Persistent chat messages, user contacts, and session state
- **Identity Engine** — Curve25519 keypair generation, Signed Pre-Keys, and One-Time PreKey management
- **Security Console** — Threat assessment, biometric lock, Play Integrity, and certificate pinning toggles
- **Network Terminal** — Live encrypted packet trace logs and offline message queue

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to sync Gradle and resolve dependencies
4. Build and run the app on an emulator or physical device

> **Note:** The debug signing configuration uses the default Android debug keystore. No additional keystore setup is required for development builds.
