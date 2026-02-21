// ─── Config Loader ─────────────────────────────────────────────────
// Centralizes all environment variable access with validation.

require('dotenv').config();

const required = (key) => {
    const val = process.env[key];
    if (!val) {
        console.error(`⚠️  CRITICAL: Missing required env var: ${key}`);
        console.error(`   Set this in Railway Variables tab before deploying.`);
        return `MISSING_${key}`;
    }
    return val;
};

const optional = (key, fallback) => process.env[key] || fallback;

module.exports = Object.freeze({
    port: parseInt(optional('PORT', '3000'), 10),
    nodeEnv: optional('NODE_ENV', 'development'),

    // Security
    apiSecretKey: required('API_SECRET_KEY'),
    corsOrigin: optional('CORS_ORIGIN', '*'),

    // DeepSeek
    deepseek: {
        apiKey: required('DEEPSEEK_API_KEY'),
        baseUrl: optional('DEEPSEEK_BASE_URL', 'https://api.deepseek.com/v1'),
        model: optional('DEEPSEEK_MODEL', 'deepseek-chat'),
    },

    // Twilio (optional for MVP)
    twilio: {
        accountSid: optional('TWILIO_ACCOUNT_SID', ''),
        authToken: optional('TWILIO_AUTH_TOKEN', ''),
        phoneNumber: optional('TWILIO_PHONE_NUMBER', ''),
    },

    // Rate limiting
    rateLimit: {
        windowMs: parseInt(optional('RATE_LIMIT_WINDOW_MS', '60000'), 10),
        maxRequests: parseInt(optional('RATE_LIMIT_MAX_REQUESTS', '30'), 10),
        messageLimit: parseInt(optional('MESSAGE_RATE_LIMIT', '5'), 10),
    },
});
