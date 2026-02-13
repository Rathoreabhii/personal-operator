# Security Hardening Guide — Personal Operator

## Threat Model

| Threat | Mitigation |
|---|---|
| Unauthorized message sending | Confirmation gate on every `send_message` — no exceptions |
| Mass messaging abuse | Rate limit: 5 messages/minute, enforced server-side |
| Shell command injection | Action whitelist rejects all undefined intents; blocked pattern regex |
| API key leakage | Keys in `.env` (never committed), `EncryptedSharedPreferences` on Android |
| Man-in-the-middle | TLS/WSS enforced in production, `network_security_config.xml` blocks cleartext |
| AI hallucination risk | Server re-validates every plan at execution time, not just at proposal time |
| Call recording without consent | Explicit consent dialog before recording; TwiML includes audible disclosure |
| Privilege escalation | Backend enforces `confirmation_required = true` server-side regardless of AI output |
| Replay attacks | Request IDs (UUID) on every WebSocket message |

## Data Security

### At Rest
- **Android**: `EncryptedSharedPreferences` using AES-256-GCM via Android Keystore
- **Backend**: Environment variables loaded at runtime, never stored in code
- **Logs**: Audit logs do not contain full message content — only intent, risk level, and metadata

### In Transit
- **Production**: WSS (WebSocket Secure) and HTTPS only
- **TLS pinning**: Recommended for production Android builds via OkHttp `CertificatePinner`

## Permission Model

| Permission | Purpose | Risk |
|---|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read WhatsApp/Telegram notifications | High — requires user enablement |
| `SYSTEM_ALERT_WINDOW` | Floating bubble + confirmation panel | Medium |
| `CALL_PHONE` | Native cellular calls | High — gated by confirmation |
| `RECORD_AUDIO` | VoIP call recording | High — requires explicit consent |
| `INTERNET` | Backend communication | Low |

## Hardening Checklist

- [x] No auto-send without confirmation
- [x] No mass messaging (rate limited)
- [x] No shell/CMD execution (not in whitelist)
- [x] No file deletion (not in whitelist)
- [x] High-risk actions require double confirmation
- [x] Call recording requires explicit consent
- [x] AES-256 encrypted storage on device
- [x] API keys never in source code
- [x] Full audit logging with timestamps
- [x] WebSocket authentication with timeout
- [x] Blocked dangerous regex patterns in parameters
- [x] Server re-validates at execution time
- [ ] **TODO**: Certificate pinning for production
- [ ] **TODO**: Biometric unlock for kill switch deactivation
- [ ] **TODO**: Remote wipe capability
