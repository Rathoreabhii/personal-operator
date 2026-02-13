// ─── REST API Routes ───────────────────────────────────────────────

const express = require('express');
const router = express.Router();
const { processMessage } = require('../services/deepseek');
const { validateAction } = require('../services/actionValidator');
const { initiateVoipCall, createNativeCallInstruction, getCall } = require('../services/callManager');
const { summarizeCall } = require('../services/callSummarizer');
const { apiKeyAuth, messageRateLimiter } = require('../middleware/security');
const { auditLog } = require('../middleware/auditLogger');

// ─── Health check ──────────────────────────────────────────────────
// This must be ABOVE the apiKeyAuth middleware for Railway to monitor it
router.get('/health', (_req, res) => {
    res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// All routes below require API key auth
router.use(apiKeyAuth);

// ─── POST /api/process-message ─────────────────────────────────────
// Receives a message context, sends to DeepSeek, returns validated action plan.
router.post('/process-message', messageRateLimiter, async (req, res) => {
    try {
        const { mode, sender, message, timestamp } = req.body;

        if (!mode || !sender || !message) {
            return res.status(400).json({
                error: 'Missing required fields: mode, sender, message',
            });
        }

        if (!['command', 'suggestion'].includes(mode)) {
            return res.status(400).json({
                error: 'mode must be "command" or "suggestion"',
            });
        }

        // 1. Get AI response
        const plan = await processMessage({
            mode,
            sender,
            message,
            timestamp: timestamp || new Date().toISOString(),
        });

        // 2. Validate against whitelist
        const validation = validateAction(plan);

        if (!validation.valid) {
            return res.status(422).json({
                error: 'Action rejected by safety validator',
                reason: validation.reason,
                human_text: 'Yeh action safe nahi lag raha. Reject kar diya.',
            });
        }

        // 3. Return sanitized plan to client
        return res.json({
            success: true,
            plan: validation.sanitizedPlan,
        });
    } catch (err) {
        return res.status(500).json({
            error: 'Failed to process message',
            detail: err.message,
        });
    }
});

// ─── POST /api/execute-action ──────────────────────────────────────
// Executes a confirmed action. Client must send the plan + confirmation.
router.post('/execute-action', async (req, res) => {
    try {
        const { plan, confirmed, double_confirmed } = req.body;

        if (!plan || !plan.intent) {
            return res.status(400).json({ error: 'Missing action plan' });
        }

        if (!confirmed) {
            return res.status(400).json({ error: 'Action not confirmed by user' });
        }

        // Re-validate on execution
        const validation = validateAction(plan);
        if (!validation.valid) {
            return res.status(422).json({
                error: 'Action rejected on re-validation',
                reason: validation.reason,
            });
        }

        // Check double confirmation for high-risk
        if (['high', 'critical'].includes(plan.risk_level) && !double_confirmed) {
            return res.status(400).json({
                error: 'High-risk actions require double confirmation',
                requires_double_confirm: true,
            });
        }

        auditLog('action_confirmed', {
            intent: plan.intent,
            riskLevel: plan.risk_level,
            params: plan.params,
        });

        // Execute based on intent
        let result;
        switch (plan.intent) {
            case 'send_message':
                // The Android client handles the actual WA message sending.
                // Backend just acknowledges and logs.
                result = {
                    action: 'send_message',
                    status: 'dispatched_to_client',
                    to: plan.params.to,
                    message: plan.params.message,
                };
                break;

            case 'call_number':
                if (plan.params.phone) {
                    // VoIP call
                    result = await initiateVoipCall(
                        plan.params.phone,
                        plan.params.script || '',
                        plan.params.record_consent || false
                    );
                } else {
                    // Native call instruction
                    result = createNativeCallInstruction(plan.params.phone || '');
                }
                break;

            case 'open_app':
                result = {
                    action: 'open_app',
                    status: 'dispatched_to_client',
                    package: plan.params.package,
                };
                break;

            case 'info_response':
                result = {
                    action: 'info_response',
                    answer: plan.params.answer,
                };
                break;

            case 'summarize_call':
                result = { action: 'summarize_call', status: 'see /api/call/:id/summary' };
                break;

            default:
                return res.status(422).json({ error: 'Unknown intent after validation' });
        }

        auditLog('action_executed', { intent: plan.intent, result });

        return res.json({ success: true, result });
    } catch (err) {
        return res.status(500).json({
            error: 'Action execution failed',
            detail: err.message,
        });
    }
});

// ─── POST /api/call/initiate ───────────────────────────────────────
// Direct call initiation endpoint.
router.post('/call/initiate', async (req, res) => {
    try {
        const { phone, script, type, record_consent } = req.body;

        if (!phone) {
            return res.status(400).json({ error: 'Missing phone number' });
        }

        let result;
        if (type === 'native') {
            result = createNativeCallInstruction(phone);
        } else {
            result = await initiateVoipCall(phone, script || '', record_consent || false);
        }

        return res.json({ success: true, call: result });
    } catch (err) {
        return res.status(500).json({
            error: 'Call initiation failed',
            detail: err.message,
        });
    }
});

// ─── GET /api/call/:id/summary ─────────────────────────────────────
// Returns call summary if available.
router.get('/call/:id/summary', async (req, res) => {
    try {
        const call = getCall(req.params.id);
        if (!call) {
            return res.status(404).json({ error: 'Call not found' });
        }

        // In production, fetch transcript from Twilio recording → speech-to-text
        // For now, accept transcript from body in POST version
        return res.json({
            callId: req.params.id,
            call,
            note: 'Use POST /api/call/:id/summarize with transcript to generate summary.',
        });
    } catch (err) {
        return res.status(500).json({ error: err.message });
    }
});

// ─── POST /api/call/:id/summarize ──────────────────────────────────
// Accepts transcript, returns AI summary.
router.post('/call/:id/summarize', async (req, res) => {
    try {
        const { transcript } = req.body;
        if (!transcript) {
            return res.status(400).json({ error: 'Missing transcript' });
        }

        const summary = await summarizeCall(transcript, req.params.id);
        return res.json({ success: true, summary });
    } catch (err) {
        return res.status(500).json({
            error: 'Summarization failed',
            detail: err.message,
        });
    }
});


module.exports = router;
