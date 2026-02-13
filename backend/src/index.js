// ─── Entry Point ───────────────────────────────────────────────────
// Initializes Express server, mounts middleware, routes, and WebSocket.

const http = require('http');
const express = require('express');
const path = require('path');
const fs = require('fs');
const config = require('./config');
const { securityMiddleware } = require('./middleware/security');
const { requestLogger, logger } = require('./middleware/auditLogger');
const apiRoutes = require('./routes/api');
const { initWebSocket } = require('./websocket/gateway');

// Ensure logs directory exists
const logsDir = path.join(__dirname, '..', 'logs');
if (!fs.existsSync(logsDir)) {
    fs.mkdirSync(logsDir, { recursive: true });
}

const app = express();

// ── Body parsing ──
app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true }));

// ── Security ──
app.use(...securityMiddleware());

// ── Audit logging ──
app.use(requestLogger);

// ── API routes ──
app.use('/api', apiRoutes);

// ── Root ──
app.get('/', (_req, res) => {
    res.json({
        service: 'Personal Operator Backend',
        version: '1.0.0',
        status: 'running',
        endpoints: {
            health: 'GET /api/health',
            processMessage: 'POST /api/process-message',
            executeAction: 'POST /api/execute-action',
            initiateCall: 'POST /api/call/initiate',
            callSummary: 'GET /api/call/:id/summary',
            summarizeCall: 'POST /api/call/:id/summarize',
            websocket: 'WS /ws',
        },
    });
});

// ── 404 ──
app.use((_req, res) => {
    res.status(404).json({ error: 'Not found' });
});

// ── Error handler ──
app.use((err, _req, res, _next) => {
    logger.error('Unhandled error', { error: err.message, stack: err.stack });
    res.status(500).json({ error: 'Internal server error' });
});

// ── Start server ──
const server = http.createServer(app);

// Initialize WebSocket on the same HTTP server
initWebSocket(server);

server.listen(config.port, () => {
    logger.info(`Server listening on port ${config.port}`);
    logger.info(`WebSocket ready at ws://localhost:${config.port}/ws`);
    logger.info(`Environment: ${config.nodeEnv}`);
});

module.exports = { app, server };
