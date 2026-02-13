// ─── Call Summarizer ───────────────────────────────────────────────
// Post-call pipeline: transcript → DeepSeek → structured summary.

const config = require('../config');
const { auditLog, logger } = require('../middleware/auditLogger');

const SUMMARY_PROMPT = `You are a call summarization assistant.
You receive a call transcript and must return a JSON summary.

## RULES
1. Return ONLY valid JSON. No markdown, no prose.
2. Use Hinglish tone in the summary text.
3. Be precise and actionable.

## RESPONSE SCHEMA
{
  "summary": "<string: 2-3 sentence overview in Hinglish>",
  "key_points": ["<point 1>", "<point 2>"],
  "decisions_made": ["<decision 1>"],
  "action_items": [
    {
      "task": "<what needs to be done>",
      "owner": "<who is responsible>",
      "deadline": "<if mentioned, else null>"
    }
  ],
  "tone": "<string: friendly | neutral | tense | formal>",
  "follow_up_needed": <boolean>
}`;

/**
 * Summarizes a call transcript using DeepSeek.
 * @param {string} transcript — the full call transcript text
 * @param {string} callId — for audit logging
 * @returns {object} structured call summary
 */
async function summarizeCall(transcript, callId) {
    if (!transcript || transcript.trim().length === 0) {
        throw new Error('Cannot summarize empty transcript');
    }

    auditLog('action_proposed', {
        action: 'summarize_call',
        callId,
        transcriptLength: transcript.length,
    });

    const requestBody = {
        model: config.deepseek.model,
        messages: [
            { role: 'system', content: SUMMARY_PROMPT },
            { role: 'user', content: `Call ID: ${callId}\n\nTranscript:\n${transcript}` },
        ],
        temperature: 0.2,
        max_tokens: 1024,
        response_format: { type: 'json_object' },
    };

    try {
        const fetch = (await import('node-fetch')).default;

        const response = await fetch(`${config.deepseek.baseUrl}/chat/completions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${config.deepseek.apiKey}`,
            },
            body: JSON.stringify(requestBody),
            signal: AbortSignal.timeout(30_000),
        });

        if (!response.ok) {
            const errBody = await response.text();
            throw new Error(`DeepSeek error ${response.status}: ${errBody}`);
        }

        const data = await response.json();
        const rawContent = data.choices?.[0]?.message?.content;

        if (!rawContent) {
            throw new Error('Empty response from DeepSeek for summary');
        }

        const summary = JSON.parse(rawContent);

        // Validate required fields
        const requiredFields = ['summary', 'key_points', 'action_items'];
        for (const field of requiredFields) {
            if (!(field in summary)) {
                throw new Error(`Summary missing required field: ${field}`);
            }
        }

        auditLog('action_executed', {
            action: 'call_summarized',
            callId,
            keyPointsCount: summary.key_points?.length || 0,
            actionItemsCount: summary.action_items?.length || 0,
        });

        return summary;
    } catch (err) {
        logger.error('Call summarization failed', { callId, error: err.message });
        throw err;
    }
}

module.exports = { summarizeCall };
