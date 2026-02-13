// ─── Audit Logger ──────────────────────────────────────────────────
// Structured JSON logging for every request and action execution.

const winston = require('winston');
const path = require('path');
const { v4: uuidv4 } = require('uuid');

const logsDir = path.join(__dirname, '..', '..', 'logs');

const logger = winston.createLogger({
    level: 'info',
    format: winston.format.combine(
        winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss.SSS' }),
        winston.format.json()
    ),
    defaultMeta: { service: 'personal-operator' },
    transports: [
        new winston.transports.File({
            filename: path.join(logsDir, 'audit.log'),
            maxsize: 10 * 1024 * 1024, // 10 MB
            maxFiles: 10,
        }),
        new winston.transports.File({
            filename: path.join(logsDir, 'error.log'),
            level: 'error',
            maxsize: 5 * 1024 * 1024,
            maxFiles: 5,
        }),
    ],
});

// Console output in development
if (process.env.NODE_ENV !== 'production') {
    logger.add(
        new winston.transports.Console({
            format: winston.format.combine(
                winston.format.colorize(),
                winston.format.printf(({ timestamp, level, message, ...meta }) => {
                    const metaStr = Object.keys(meta).length > 1 ? ` ${JSON.stringify(meta)}` : '';
                    return `${timestamp} [${level}] ${message}${metaStr}`;
                })
            ),
        })
    );
}

/**
 * Log an auditable action.
 * @param {'request'|'action_proposed'|'action_confirmed'|'action_executed'|'action_rejected'|'error'} eventType
 * @param {object} details
 */
function auditLog(eventType, details = {}) {
    const entry = {
        eventId: uuidv4(),
        eventType,
        ...details,
    };
    logger.info(entry.eventType, entry);
    return entry.eventId;
}

/**
 * Express middleware — logs every HTTP request.
 */
function requestLogger(req, res, next) {
    const start = Date.now();
    res.on('finish', () => {
        auditLog('request', {
            method: req.method,
            path: req.path,
            status: res.statusCode,
            durationMs: Date.now() - start,
            ip: req.ip,
        });
    });
    next();
}

module.exports = { logger, auditLog, requestLogger };
