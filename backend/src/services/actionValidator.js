// ─── Action Validator ──────────────────────────────────────────────
// Whitelist engine — only allows known-safe intents.
// Rejects any undefined or potentially dangerous action.

const { auditLog, logger } = require('../middleware/auditLogger');

/**
 * Exhaustive whitelist of allowed action intents.
 * Any intent NOT on this list is REJECTED.
 */
const ACTION_WHITELIST = new Set([
    'send_message',
    'call_number',
    'summarize_call',
    'open_app',
    'info_response',
]);

/**
 * Actions that are information-only and don't modify state.
 * These still require confirmation but are lower risk.
 */
const PASSIVE_INTENTS = new Set(['info_response', 'summarize_call']);

/**
 * Dangerous patterns that must NEVER appear in any parameter value.
 */
const BLOCKED_PATTERNS = [
    /rm\s+-rf/i,
    /del\s+\/[sfq]/i,
    /format\s+[a-z]:/i,
    /DROP\s+TABLE/i,
    /exec\(/i,
    /eval\(/i,
    /\bsudo\b/i,
    /\bshell\b/i,
    /\bcmd\.exe\b/i,
    /broadcast/i,           // No mass messaging
    /bulk/i,                // No bulk operations
    /mass[_\s]?message/i,   // No mass messaging
];

/**
 * Validates an action plan against the whitelist and security rules.
 * @param {object} plan — parsed DeepSeek action plan
 * @returns {{ valid: boolean, reason?: string, sanitizedPlan?: object }}
 */
function validateAction(plan) {
    // 1. Check intent is on whitelist
    if (!plan.intent || !ACTION_WHITELIST.has(plan.intent)) {
        const reason = `Blocked: intent "${plan.intent}" is not in the allowed whitelist.`;
        auditLog('action_rejected', { intent: plan.intent, reason });
        return { valid: false, reason };
    }

    // 2. Check confirmation_required is true (enforce server-side)
    if (plan.confirmation_required !== true) {
        const reason = 'Blocked: confirmation_required must be true.';
        auditLog('action_rejected', { intent: plan.intent, reason });
        return { valid: false, reason };
    }

    // 3. Scan all param values for blocked patterns
    const paramStr = JSON.stringify(plan.params || {});
    for (const pattern of BLOCKED_PATTERNS) {
        if (pattern.test(paramStr)) {
            const reason = `Blocked: parameter values contain dangerous pattern: ${pattern}`;
            auditLog('action_rejected', { intent: plan.intent, reason, params: paramStr.substring(0, 200) });
            return { valid: false, reason };
        }
    }

    // 4. Scan execution_plan steps for blocked patterns
    const stepsStr = JSON.stringify(plan.execution_plan || []);
    for (const pattern of BLOCKED_PATTERNS) {
        if (pattern.test(stepsStr)) {
            const reason = `Blocked: execution plan contains dangerous pattern: ${pattern}`;
            auditLog('action_rejected', { intent: plan.intent, reason });
            return { valid: false, reason };
        }
    }

    // 5. Validate intent-specific params
    const paramCheck = validateIntentParams(plan.intent, plan.params);
    if (!paramCheck.valid) {
        auditLog('action_rejected', { intent: plan.intent, reason: paramCheck.reason });
        return paramCheck;
    }

    // 6. Determine if double confirmation is needed
    const requiresDoubleConfirm = ['high', 'critical'].includes(plan.risk_level);

    auditLog('action_proposed', {
        intent: plan.intent,
        riskLevel: plan.risk_level,
        requiresDoubleConfirm,
        isPassive: PASSIVE_INTENTS.has(plan.intent),
    });

    return {
        valid: true,
        sanitizedPlan: {
            ...plan,
            confirmation_required: true,
            requires_double_confirm: requiresDoubleConfirm,
            is_passive: PASSIVE_INTENTS.has(plan.intent),
        },
    };
}

/**
 * Validates that the required parameters exist for each intent.
 */
function validateIntentParams(intent, params) {
    if (!params || typeof params !== 'object') {
        return { valid: false, reason: 'Missing params object.' };
    }

    switch (intent) {
        case 'send_message':
            if (!params.to || typeof params.to !== 'string') {
                return { valid: false, reason: 'send_message requires params.to (string).' };
            }
            if (!params.message || typeof params.message !== 'string') {
                return { valid: false, reason: 'send_message requires params.message (string).' };
            }
            // Length limit — prevent abuse
            if (params.message.length > 2000) {
                return { valid: false, reason: 'Message too long. Max 2000 characters.' };
            }
            break;

        case 'call_number':
            if (!params.to || typeof params.to !== 'string') {
                return { valid: false, reason: 'call_number requires params.to (string).' };
            }
            break;

        case 'summarize_call':
            if (!params.call_id || typeof params.call_id !== 'string') {
                return { valid: false, reason: 'summarize_call requires params.call_id (string).' };
            }
            break;

        case 'open_app':
            if (!params.package || typeof params.package !== 'string') {
                return { valid: false, reason: 'open_app requires params.package (string).' };
            }
            break;

        case 'info_response':
            if (!params.answer || typeof params.answer !== 'string') {
                return { valid: false, reason: 'info_response requires params.answer (string).' };
            }
            break;

        default:
            return { valid: false, reason: `Unknown intent: ${intent}` };
    }

    return { valid: true };
}

module.exports = { validateAction, ACTION_WHITELIST, PASSIVE_INTENTS };
