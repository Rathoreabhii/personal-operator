# Personal Operator — AI-Powered Assistant

> Secure, modular AI assistant with WhatsApp conversational control, supervised automation, and call handling.

## Architecture

```
Android App ←── WSS ──→ Node.js Backend ←── HTTPS ──→ DeepSeek API
                                         ←── HTTPS ──→ Twilio VoIP
```

## Quick Start

### 1. Backend Setup

```bash
cd backend
cp .env.example .env
# Edit .env with your DeepSeek API key and other secrets
npm install
npm run dev
```

Server starts on `http://localhost:3000` with WebSocket at `ws://localhost:3000/ws`.

### 2. Android App

1. Open `android/PersonalOperator` in Android Studio
2. Sync Gradle
3. Build and install on your Android device
4. Grant permissions:
   - **Notification Access**: Settings → Notifications → Notification access → Enable Personal Operator
   - **Overlay**: Prompted on first launch
   - **Phone, Audio, Contacts**: Prompted on first launch
5. Configure in app: enter your backend server URL and API key

### 3. Test the Flow

1. Send a message to your own WhatsApp number: *"Call Rahul and tell him meeting shifted"*
2. The app captures the notification and sends to backend
3. DeepSeek returns a structured plan
4. Confirmation panel slides in: *"Main Rahul ko call karu? Confirm kar do."*
5. Tap **Confirm** to execute

## Project Structure

```
evo/
├── backend/
│   ├── src/
│   │   ├── index.js              # Server entry point
│   │   ├── config.js             # Env config loader
│   │   ├── middleware/
│   │   │   ├── security.js       # Helmet, CORS, rate limiting
│   │   │   └── auditLogger.js    # Structured audit logging
│   │   ├── services/
│   │   │   ├── deepseek.js       # DeepSeek API client
│   │   │   ├── actionValidator.js # Whitelist validator
│   │   │   ├── callManager.js    # Twilio VoIP + native calls
│   │   │   └── callSummarizer.js # Post-call AI summary
│   │   ├── routes/
│   │   │   └── api.js            # REST endpoints
│   │   ├── websocket/
│   │   │   └── gateway.js        # WebSocket server
│   │   └── prompts/
│   │       └── systemPrompt.js   # DeepSeek system prompt
│   ├── package.json
│   └── .env.example
│
├── android/PersonalOperator/
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/evo/operator/
│           ├── MainActivity.kt
│           ├── OperatorApplication.kt
│           ├── model/ActionPlan.kt
│           ├── security/SecureStorage.kt
│           ├── network/WebSocketClient.kt
│           ├── service/
│           │   ├── NotificationListener.kt
│           │   ├── OperatorForegroundService.kt
│           │   └── CallService.kt
│           ├── tts/HinglishTTS.kt
│           └── ui/
│               ├── overlay/
│               │   ├── FloatingBubble.kt
│               │   └── ConfirmationPanel.kt
│               └── screens/
│                   └── DashboardScreen.kt
│
└── docs/
    ├── SECURITY.md
    └── MVP_ROADMAP.md
```

## Security

- **No auto-send** — every action requires explicit confirmation
- **Whitelist-only** — only `send_message`, `call_number`, `summarize_call`, `open_app` allowed
- **AES-256 encrypted** storage on device
- **Full audit logging** — every action tracked
- **Kill switch** — instantly stop all operations
- **Double confirm** for high-risk actions

See [docs/SECURITY.md](docs/SECURITY.md) for the full hardening guide.

## License

Private. All rights reserved.
