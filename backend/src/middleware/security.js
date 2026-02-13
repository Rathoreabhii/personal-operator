// ─── Security Middleware ────────────────────────────────────────────
// Helmet, CORS, rate limiting, and API-key authentication.

const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const config = require('../config');

/**
 * Returns an array of Express middleware for security hardening.
 */
function securityMiddleware() {
    return [
        // HTTP security headers
        helmet(),

        // CORS (simple approach; extend for production)
        (req, res, next) => {
            res.setHeader('Access-Control-Allow-Origin', config.corsOrigin);
            res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
            res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-API-Key');
            if (req.method === 'OPTIONS') return res.sendStatus(204);
            next();
        },

        // Global rate limiter
        rateLimit({
            windowMs: config.rateLimit.windowMs,
            max: config.rateLimit.maxRequests,
            standardHeaders: true,
            legacyHeaders: false,
            message: { error: 'Too many requests. Slow down.' },
        }),
    ];
}

/**
 * API key authentication middleware.
 * Expects header: X-API-Key: <secret>
 */
function apiKeyAuth(req, res, next) {
    const key = req.headers['x-api-key'];
    if (!key || key !== config.apiSecretKey) {
        return res.status(401).json({ error: 'Unauthorized — invalid or missing API key.' });
    }
    next();
}

/**
 * Message-level rate limiter — max N messages per minute per client.
 */
const messageRateLimiter = rateLimit({
    windowMs: 60_000,
    max: config.rateLimit.messageLimit,
    keyGenerator: (req) => req.headers['x-api-key'] || req.ip,
    message: { error: 'Message rate limit exceeded. Max 5 messages per minute.' },
});

module.exports = { securityMiddleware, apiKeyAuth, messageRateLimiter };
