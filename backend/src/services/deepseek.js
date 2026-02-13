// ─── DeepSeek AI Client ────────────────────────────────────────────
// Integrates with DeepSeek v3.2 Chat Completions API.
// Sends system prompt + user context, parses strict JSON response.

const config = require('../config');
const SYSTEM_PROMPT = require('../prompts/systemPrompt');
const { auditLog, logger } = require('../middleware/auditLogger');

const RESPONSE_SCHEMA_KEYS = [
    'intent',
    'confidence',
    'risk_level',
    'human_text',
    'params',
    'execution_plan',
    'confirmation_required',
];

const VALID_INTENTS = [
    'send_message',
    'call_number',
    'summarize_call',
    'open_app',
    'info_response',
];

const VALID_RISK_LEVELS = ['low', 'medium', 'high', 'critical'];

/**
 * Calls DeepSeek API with the given user message context.
 * @param {object} messageContext — { mode, sender, message, timestamp }
 * @returns {object} parsed and validated AI action plan
 */
async function processMessage(messageContext) {
    const { mode, sender, message, timestamp } = messageContext;

    const userContent = JSON.stringify({ mode, sender, message, timestamp });

    const requestBody = {
        model: config.deepseek.model,
        messages: [
            { role: 'system', content: SYSTEM_PROMPT },
            { role: 'user', content: userContent },
        ],
        temperature: 0.3,
        max_tokens: 1024,
        response_format: { type: 'json_object' },
    };

    auditLog('action_proposed', {
        service: 'deepseek',
        input: { mode, sender, messagePreview: message.substring(0, 100) },
    });

    let response;
    try {
        // Dynamic import for node-fetch (ESM)
        const fetch = (await import('node-fetch')).default;

        response = await fetch(`${config.deepseek.baseUrl}/chat/completions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${config.deepseek.apiKey}`,
            },
            body: JSON.stringify(requestBody),
            signal: AbortSignal.timeout(30_000),
        });
    } catch (err) {
        logger.error('DeepSeek API request failed', { error: err.message });
        throw new Error(`DeepSeek API unreachable: ${err.message}`);
    }

    if (!response.ok) {
        const errorBody = await response.text();
        logger.error('DeepSeek API error', { status: response.status, body: errorBody });
        throw new Error(`DeepSeek API error ${response.status}: ${errorBody}`);
    }

    const data = await response.json();
    const rawContent = data.choices?.[0]?.message?.content;

    if (!rawContent) {
        throw new Error('DeepSeek returned empty response');
    }

    // Parse + validate
    let plan;
    try {
        plan = JSON.parse(rawContent);
    } catch {
        logger.error('DeepSeek returned non-JSON', { raw: rawContent.substring(0, 500) });
        throw new Error('DeepSeek response is not valid JSON');
    }

    validatePlan(plan);

    // SECURITY: Force confirmation_required = true regardless of what AI says
    plan.confirmation_required = true;

    auditLog('action_proposed', {
        service: 'deepseek',
        intent: plan.intent,
        riskLevel: plan.risk_level,
        confidence: plan.confidence,
    });

    return plan;
}

/**
 * Validates the parsed plan against the expected schema.
 */
function validatePlan(plan) {
    // Check all required keys exist
    for (const key of RESPONSE_SCHEMA_KEYS) {
        if (!(key in plan)) {
            throw new Error(`DeepSeek response missing required field: ${key}`);
        }
    }

    // Validate intent
    if (!VALID_INTENTS.includes(plan.intent)) {
        throw new Error(`Invalid intent from DeepSeek: ${plan.intent}`);
    }

    // Validate risk level
    if (!VALID_RISK_LEVELS.includes(plan.risk_level)) {
        throw new Error(`Invalid risk_level: ${plan.risk_level}`);
    }

    // Validate confidence range
    if (typeof plan.confidence !== 'number' || plan.confidence < 0 || plan.confidence > 1) {
        throw new Error(`Invalid confidence value: ${plan.confidence}`);
    }

    // Validate execution_plan is array
    if (!Array.isArray(plan.execution_plan)) {
        throw new Error('execution_plan must be an array');
    }

    // Validate human_text is non-empty string
    if (typeof plan.human_text !== 'string' || plan.human_text.trim().length === 0) {
        throw new Error('human_text must be a non-empty string');
    }
}

module.exports = { processMessage, validatePlan, VALID_INTENTS, VALID_RISK_LEVELS };
