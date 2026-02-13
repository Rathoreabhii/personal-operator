# MVP Build Roadmap — Personal Operator

## Phase 1: Foundation (Week 1-2)

**Goal**: Backend running, Android app connecting, basic notification capture.

| Task | Status |
|---|---|
| Backend scaffolding (Express + WebSocket) | ✅ Done |
| DeepSeek API integration | ✅ Done |
| Action whitelist validator | ✅ Done |
| Audit logging | ✅ Done |
| Android project setup | ✅ Done |
| NotificationListenerService | ✅ Done |
| WebSocket client with auth | ✅ Done |
| EncryptedSharedPreferences | ✅ Done |

**Milestone**: Capture a WhatsApp notification → send to backend → get DeepSeek response → display in app.

---

## Phase 2: Conversational Control (Week 3-4)

**Goal**: Full WhatsApp conversational flow with confirmation.

| Task | Status |
|---|---|
| Self-message detection (command mode) | ✅ Done |
| Suggestion mode for external messages | ✅ Done  |
| Floating bubble overlay | ✅ Done |
| Slide-in confirmation panel | ✅ Done |
| Double confirmation for high-risk | ✅ Done |
| Kill switch | ✅ Done |
| Dashboard UI | ✅ Done |

**Milestone**: User sends "Call Rahul" to their own WhatsApp → gets Hinglish confirmation → approves → action executes.

---

## Phase 3: Calling & TTS (Week 5-6)

**Goal**: Both call modes working with Hinglish TTS.

| Task | Status |
|---|---|
| Native cellular call (ACTION_CALL) | ✅ Done |
| VoIP call via Twilio | ✅ Done |
| Hinglish TTS engine | ✅ Done |
| Call recording with consent | ✅ Done |
| Call summarization pipeline | ✅ Done |
| Summary display in app | 🔲 TODO |

**Milestone**: User says "Call Rahul, tell him meeting shifted" → VoIP call placed → TTS speaks in Hinglish → call summarized.

---

## Phase 4: Polish & Hardening (Week 7-8)

**Goal**: Production-ready security, testing, and documentation.

| Task | Status |
|---|---|
| TLS certificate pinning | 🔲 TODO |
| Biometric auth for kill switch | 🔲 TODO |
| Comprehensive test suite | 🔲 TODO |
| Settings screen (server URL, phone config) | 🔲 TODO |
| Telegram full support | 🔲 TODO |
| Remote wipe capability | 🔲 TODO |
| Performance optimization | 🔲 TODO |
| Play Store / sideload packaging | 🔲 TODO |

**Milestone**: Fully tested, hardened, and deployable system.
