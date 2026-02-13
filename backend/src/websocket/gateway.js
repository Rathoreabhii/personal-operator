// ─── WebSocket Gateway ─────────────────────────────────────────────
// Bidirectional communication between Android client and backend.
// Handles authentication, message routing, and push notifications.

const { WebSocketServer } = require('ws');
const { processMessage } = require('../services/deepseek');
const { validateAction } = require('../services/actionValidator');
const { auditLog, logger } = require('../middleware/auditLogger');
const config = require('../config');

/** @type {Map<string, import('ws').WebSocket>} */
const authenticatedClients = new Map();

/**
 * Initializes the WebSocket server on an existing HTTP server.
 * @param {import('http').Server} server
 */
function initWebSocket(server) {
    const wss = new WebSocketServer({ server, path: '/ws' });

    wss.on('connection', (ws, req) => {
        const clientIp = req.socket.remoteAddress;
        let clientId = null;
        let authenticated = false;

        logger.info('WebSocket connection attempt', { ip: clientIp });

        // Authentication timeout — client must auth within 10 seconds
        const authTimeout = setTimeout(() => {
            if (!authenticated) {
                ws.close(4001, 'Authentication timeout');
                logger.warn('WebSocket auth timeout', { ip: clientIp });
            }
        }, 10_000);

        // Heartbeat
        ws.isAlive = true;
        ws.on('pong', () => {
            ws.isAlive = true;
        });

        ws.on('message', async (rawData) => {
            let msg;
            try {
                msg = JSON.parse(rawData.toString());
            } catch {
                ws.send(JSON.stringify({ type: 'error', message: 'Invalid JSON' }));
                return;
            }

            // ── Authentication ──
            if (msg.type === 'auth') {
                if (msg.apiKey === config.apiSecretKey) {
                    authenticated = true;
                    clientId = msg.clientId || `client_${Date.now()}`;
                    authenticatedClients.set(clientId, ws);
                    clearTimeout(authTimeout);

                    ws.send(JSON.stringify({ type: 'auth_success', clientId }));
                    auditLog('request', { event: 'ws_authenticated', clientId, ip: clientIp });
                    logger.info('WebSocket client authenticated', { clientId });
                } else {
                    ws.send(JSON.stringify({ type: 'auth_failed', message: 'Invalid API key' }));
                    ws.close(4003, 'Invalid API key');
                }
                return;
            }

            // All other messages require authentication
            if (!authenticated) {
                ws.send(JSON.stringify({ type: 'error', message: 'Not authenticated' }));
                return;
            }

            // ── Message Processing ──
            if (msg.type === 'notification') {
                try {
                    const { mode, sender, message, timestamp, packageName } = msg.data;

                    auditLog('request', {
                        event: 'notification_received',
                        clientId,
                        mode,
                        sender,
                        packageName,
                    });

                    // Process through DeepSeek
                    const plan = await processMessage({ mode, sender, message, timestamp });

                    // Validate
                    const validation = validateAction(plan);

                    if (!validation.valid) {
                        ws.send(JSON.stringify({
                            type: 'action_rejected',
                            requestId: msg.requestId,
                            reason: validation.reason,
                            human_text: 'Yeh action safe nahi lag raha. Reject kar diya.',
                        }));
                        return;
                    }

                    // Send plan back to client for confirmation
                    ws.send(JSON.stringify({
                        type: 'action_proposed',
                        requestId: msg.requestId,
                        plan: validation.sanitizedPlan,
                    }));

                } catch (err) {
                    logger.error('WS notification processing error', { error: err.message, clientId });
                    ws.send(JSON.stringify({
                        type: 'error',
                        requestId: msg.requestId,
                        message: `Processing failed: ${err.message}`,
                    }));
                }
                return;
            }

            // ── Action Confirmation ──
            if (msg.type === 'action_confirm') {
                const { plan, double_confirmed } = msg.data;

                if (['high', 'critical'].includes(plan.risk_level) && !double_confirmed) {
                    ws.send(JSON.stringify({
                        type: 'double_confirm_required',
                        requestId: msg.requestId,
                        plan,
                    }));
                    return;
                }

                auditLog('action_confirmed', {
                    clientId,
                    intent: plan.intent,
                    riskLevel: plan.risk_level,
                });

                // Dispatch execution instruction back to client
                ws.send(JSON.stringify({
                    type: 'execute',
                    requestId: msg.requestId,
                    plan,
                }));
                return;
            }

            // ── Action Rejection ──
            if (msg.type === 'action_reject') {
                auditLog('action_rejected', {
                    clientId,
                    intent: msg.data?.intent,
                    reason: 'User rejected',
                });

                ws.send(JSON.stringify({
                    type: 'action_cancelled',
                    requestId: msg.requestId,
                    message: 'Action cancelled by user.',
                }));
                return;
            }

            // ── Ping ──
            if (msg.type === 'ping') {
                ws.send(JSON.stringify({ type: 'pong', timestamp: Date.now() }));
                return;
            }

            // Unknown message type
            ws.send(JSON.stringify({ type: 'error', message: `Unknown message type: ${msg.type}` }));
        });

        ws.on('close', () => {
            if (clientId) {
                authenticatedClients.delete(clientId);
                logger.info('WebSocket client disconnected', { clientId });
            }
        });

        ws.on('error', (err) => {
            logger.error('WebSocket error', { clientId, error: err.message });
        });
    });

    // Heartbeat interval — detect dead connections
    const heartbeatInterval = setInterval(() => {
        wss.clients.forEach((ws) => {
            if (!ws.isAlive) {
                return ws.terminate();
            }
            ws.isAlive = false;
            ws.ping();
        });
    }, 30_000);

    wss.on('close', () => {
        clearInterval(heartbeatInterval);
    });

    logger.info('WebSocket server initialized');
    return wss;
}

/**
 * Push a message to a specific authenticated client.
 */
function pushToClient(clientId, data) {
    const ws = authenticatedClients.get(clientId);
    if (ws && ws.readyState === ws.OPEN) {
        ws.send(JSON.stringify(data));
        return true;
    }
    return false;
}

module.exports = { initWebSocket, pushToClient };
