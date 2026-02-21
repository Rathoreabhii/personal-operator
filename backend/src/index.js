const fs = require('fs');
const path = require('path');

// ─── Pre-flight Setup ────────────────────────────────────────────────
// Ensure directories exist BEFORE loading any other modules (logger depends on logs/)
const logsDir = path.join(__dirname, '..', 'logs');
if (!fs.existsSync(logsDir)) {
    fs.mkdirSync(logsDir, { recursive: true });
}

const http = require('http');
const express = require('express');
const config = require('./config');
const { securityMiddleware } = require('./middleware/security');
const { requestLogger, logger } = require('./middleware/auditLogger');
const apiRoutes = require('./routes/api');
const { initWebSocket } = require('./websocket/gateway');

const app = express();

// ─── Health check (root) — Move to top for highest priority ──
app.get('/health', (_req, res) => {
    console.log('[HEALTH] Ping received');
    res.json({ status: 'ok', root: true, timestamp: new Date().toISOString() });
});

app.get('/', (_req, res) => {
    res.json({
        service: 'Personal Operator Backend',
        version: '1.0.1',
        status: 'running',
    });
});

// ── Body parsing ──
app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true }));

// ── Security ──
app.use(...securityMiddleware());

// ── Audit logging ──
app.use(requestLogger);

// ── API routes ──
app.use('/api', apiRoutes);



// ── WebSocket info (for non-upgrade GET requests to /ws) ──
app.get('/ws', (_req, res) => {
    res.json({
        message: 'WebSocket endpoint — use ws:// or wss:// protocol to connect',
        path: '/ws',
        status: 'ready',
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
server.on('error', (error) => {
    console.error('[FATAL] Server encountered an error:', error);
    if (error.code === 'EADDRINUSE') {
        process.exit(1);
    }
});

// Capture termination signals to understand WHY it stops
['SIGINT', 'SIGTERM', 'SIGQUIT'].forEach(signal => {
    process.on(signal, () => {
        console.error(`[DEBUG] Received ${signal} signal. Shutting down...`);
        server.close(() => {
            console.error('[DEBUG] Server closed.');
            process.exit(0);
        });
    });
});

// Initialize WebSocket BEFORE server.listen so upgrade handler is ready
console.log('[DEBUG] Initializing WebSocket...');
try {
    initWebSocket(server);
    console.log('[DEBUG] WebSocket initialized successfully');
} catch (err) {
    console.error('CRITICAL: WebSocket initialization failed', err);
}

console.log(`[DEBUG] Attempting to start server on port: ${config.port} (host: 0.0.0.0)`);
try {
    server.listen(config.port, '0.0.0.0', () => {
        console.log('[DEBUG] server.listen callback fired');
        const addr = server.address();
        const bind = typeof addr === 'string' ? 'pipe ' + addr : 'port ' + addr.port;
        logger.info(`Server listening on ${bind}`);
        logger.info(`Environment: ${config.nodeEnv}`);
        logger.info(`WebSocket ready at ws://0.0.0.0:${config.port}/ws`);
    });
} catch (error) {
    console.error('[FATAL] Failed to start server:', error);
    process.exit(1);
}

// Global error handlers...
process.on('uncaughtException', (err) => {
    console.error('CRITICAL: Uncaught Exception:', err);
    if (logger) logger.error('CRITICAL: Uncaught Exception', { error: err.message, stack: err.stack });
    process.exit(1);
});

process.on('unhandledRejection', (reason, promise) => {
    console.error('CRITICAL: Unhandled Rejection at:', promise, 'reason:', reason);
    if (logger) logger.error('CRITICAL: Unhandled Rejection', { reason: reason?.toString() });
});

module.exports = { app, server };
