// ─── Call Manager ──────────────────────────────────────────────────
// Handles VoIP call initiation via Twilio, recording, and transcription.
// Also provides a native-call instruction path for the Android client.

const config = require('../config');
const { auditLog, logger } = require('../middleware/auditLogger');
const { v4: uuidv4 } = require('uuid');

// In-memory call registry (replace with DB in production)
const activeCalls = new Map();

/**
 * Initiates a VoIP call via Twilio REST API.
 * @param {string} toNumber — E.164 formatted phone number
 * @param {string} ttsScript — Text for the AI to speak (Hinglish)
 * @param {boolean} recordConsent — User has given explicit consent to record
 * @returns {object} call metadata
 */
async function initiateVoipCall(toNumber, ttsScript, recordConsent = false) {
    const callId = uuidv4();

    if (!config.twilio.accountSid || !config.twilio.authToken) {
        logger.warn('Twilio not configured — returning mock call for development');
        const mockCall = {
            callId,
            status: 'mock',
            to: toNumber,
            ttsScript,
            recording: recordConsent,
            createdAt: new Date().toISOString(),
        };
        activeCalls.set(callId, mockCall);
        return mockCall;
    }

    const twilioUrl = `https://api.twilio.com/2010-04-01/Accounts/${config.twilio.accountSid}/Calls.json`;

    // Build TwiML for the call
    const twiml = buildTwiML(ttsScript, recordConsent, callId);

    const params = new URLSearchParams();
    params.append('To', toNumber);
    params.append('From', config.twilio.phoneNumber);
    params.append('Twiml', twiml);

    if (recordConsent) {
        params.append('Record', 'true');
        params.append('RecordingStatusCallback', `/api/call/${callId}/recording-status`);
    }

    try {
        const fetch = (await import('node-fetch')).default;

        const authHeader = Buffer.from(
            `${config.twilio.accountSid}:${config.twilio.authToken}`
        ).toString('base64');

        const response = await fetch(twilioUrl, {
            method: 'POST',
            headers: {
                Authorization: `Basic ${authHeader}`,
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: params.toString(),
        });

        if (!response.ok) {
            const errBody = await response.text();
            throw new Error(`Twilio error ${response.status}: ${errBody}`);
        }

        const twilioData = await response.json();

        const callMeta = {
            callId,
            twilioSid: twilioData.sid,
            status: twilioData.status,
            to: toNumber,
            ttsScript,
            recording: recordConsent,
            createdAt: new Date().toISOString(),
        };

        activeCalls.set(callId, callMeta);

        auditLog('action_executed', {
            action: 'voip_call_initiated',
            callId,
            to: toNumber,
            recording: recordConsent,
        });

        return callMeta;
    } catch (err) {
        logger.error('Failed to initiate Twilio call', { error: err.message });
        throw err;
    }
}

/**
 * Builds TwiML XML for the call.
 */
function buildTwiML(ttsScript, record, callId) {
    let twiml = '<?xml version="1.0" encoding="UTF-8"?><Response>';

    if (record) {
        twiml += '<Say voice="Polly.Aditi" language="hi-IN">';
        twiml += 'Is call ko record kiya jayega. Agar aap agree nahi karte toh disconnect kar sakte hain.';
        twiml += '</Say><Pause length="3"/>';
    }

    twiml += `<Say voice="Polly.Aditi" language="hi-IN">${escapeXml(ttsScript)}</Say>`;
    twiml += '<Pause length="2"/>';
    twiml += '<Say voice="Polly.Aditi" language="hi-IN">Kuch aur baat karni hai? Nahi toh call disconnect ho jayegi.</Say>';
    twiml += '<Pause length="5"/>';
    twiml += '</Response>';

    return twiml;
}

/**
 * Returns a native call instruction for the Android client.
 * The Android app will use ACTION_CALL intent.
 */
function createNativeCallInstruction(toNumber) {
    const callId = uuidv4();
    const instruction = {
        callId,
        type: 'native_cellular',
        to: toNumber,
        action: 'android.intent.action.CALL',
        uri: `tel:${toNumber}`,
        note: 'Native call — no TTS injection, no recording via backend.',
        createdAt: new Date().toISOString(),
    };

    activeCalls.set(callId, instruction);
    auditLog('action_executed', { action: 'native_call_instruction', callId, to: toNumber });

    return instruction;
}

/**
 * Get call metadata by ID.
 */
function getCall(callId) {
    return activeCalls.get(callId) || null;
}

function escapeXml(str) {
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&apos;');
}

module.exports = { initiateVoipCall, createNativeCallInstruction, getCall };
