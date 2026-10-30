/**
 * NK APP - Secure Gemini AI Backend Proxy
 * 
 * Responsibilities:
 * 1. Securely holds GEMINI_API_KEY on the server side - never embedded in the Android APK.
 * 2. Uses supported Gemini models (default: gemini-3.5-flash-lite with fallback to gemini-3.8-flash).
 * 3. Enforces zero persistence: no database, no file logging of prompts/responses.
 * 4. Supports Hindi, English, Hinglish, coding, mathematics, and Markdown formatting.
 * 5. Handles free-tier rate limits with friendly user messages.
 * 6. Supports standard JSON responses and Server-Sent Events (SSE) streaming.
 */

const http = require('http');
const https = require('https');
const url = require('url');

// Avoid collision with system PORT=8080 in cloud container
const PORT = parseInt(
  process.env.BACKEND_PORT ||
  (process.env.PORT && process.env.PORT !== '8080' ? process.env.PORT : '3000'),
  10
);

const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';

// Normalize model ID, upgrading deprecated 2.5-flash to currently supported 3.5-flash-lite
function resolveModelName(envModel) {
  const model = (envModel || '').trim();
  if (!model || model.includes('2.5-flash') || model.includes('1.5-flash')) {
    return 'gemini-3.5-flash-lite';
  }
  return model;
}

const PRIMARY_MODEL = resolveModelName(process.env.GEMINI_MODEL);
const FALLBACK_MODEL = PRIMARY_MODEL === 'gemini-3.5-flash-lite' ? 'gemini-3.8-flash' : 'gemini-3.5-flash-lite';

// System prompt guiding Gemini for NK APP
const SYSTEM_INSTRUCTION = `You are the AI Assistant inside NK APP.
Your capabilities and guidelines:
1. Languages: You fluently understand and respond in English, Hindi (हिंदी), and Hinglish (Hindi written in Roman script/Latin characters). Match the user's preferred language naturally.
2. Expertise: Excellent at software development, coding, mathematics, scientific reasoning, creative writing, and concise Q&A.
3. Formatting: Format your answers clearly using clean Markdown (headings, lists, code blocks with language identifiers, bold for emphasis).
4. Tone: Helpful, direct, intelligent, and polite.
5. Privacy: Respect user privacy. Never ask for personally identifiable information.`;

function sendJsonResponse(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization'
  });
  res.end(JSON.stringify(data));
}

function handleCors(req, res) {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      'Access-Control-Max-Age': '86400'
    });
    res.end();
    return true;
  }
  return false;
}

async function callGeminiGenerate(modelName, requestBody) {
  const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(modelName)}:generateContent?key=${encodeURIComponent(GEMINI_API_KEY)}`;
  return fetch(geminiUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: requestBody
  });
}

const server = http.createServer(async (req, res) => {
  if (handleCors(req, res)) return;

  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;

  // Health check endpoint
  if (req.method === 'GET' && pathname === '/api/health') {
    return sendJsonResponse(res, 200, {
      status: 'ok',
      service: 'NK APP Gemini Backend',
      model: PRIMARY_MODEL,
      hasApiKey: Boolean(GEMINI_API_KEY),
      privacy: 'Zero-history strictly in-memory'
    });
  }

  // Non-streaming chat endpoint
  if (req.method === 'POST' && pathname === '/api/chat') {
    let body = '';
    req.on('data', chunk => {
      body += chunk;
      if (body.length > 500000) {
        req.destroy();
      }
    });

    req.on('end', async () => {
      try {
        const payload = JSON.parse(body || '{}');
        const prompt = (payload.prompt || '').trim();
        const history = Array.isArray(payload.history) ? payload.history : [];

        if (!prompt && history.length === 0) {
          return sendJsonResponse(res, 400, { error: 'Prompt or history cannot be empty' });
        }

        if (!GEMINI_API_KEY) {
          return sendJsonResponse(res, 503, {
            error: 'AI service setup is incomplete: GEMINI_API_KEY is not configured on the server.'
          });
        }

        const contents = [];
        for (const item of history) {
          contents.push({
            role: item.role === 'model' ? 'model' : 'user',
            parts: [{ text: item.text }]
          });
        }
        if (prompt) {
          contents.push({
            role: 'user',
            parts: [{ text: prompt }]
          });
        }

        const requestBody = JSON.stringify({
          system_instruction: {
            parts: [{ text: SYSTEM_INSTRUCTION }]
          },
          contents: contents,
          generationConfig: {
            temperature: 0.7,
            topP: 0.95,
            maxOutputTokens: 2048
          }
        });

        // Try primary model
        let activeModel = PRIMARY_MODEL;
        let geminiRes = await callGeminiGenerate(activeModel, requestBody);

        // Fallback retry if 404 (model removed) or 503 (model overloaded)
        if (!geminiRes.ok && (geminiRes.status === 404 || geminiRes.status === 503)) {
          console.warn(`Model ${activeModel} returned ${geminiRes.status}, falling back to ${FALLBACK_MODEL}`);
          activeModel = FALLBACK_MODEL;
          geminiRes = await callGeminiGenerate(activeModel, requestBody);
        }

        if (!geminiRes.ok) {
          const errData = await geminiRes.json().catch(() => ({}));
          if (geminiRes.status === 429) {
            return sendJsonResponse(res, 429, {
              error: 'AI usage limit reached. Please try again later.'
            });
          }
          if (geminiRes.status === 503) {
            return sendJsonResponse(res, 503, {
              error: 'AI service is temporarily unavailable. Please try again in a moment.'
            });
          }
          return sendJsonResponse(res, geminiRes.status, {
            error: errData.error?.message || `AI service returned error HTTP ${geminiRes.status}`
          });
        }

        const geminiData = await geminiRes.json();
        const candidate = geminiData.candidates?.[0];
        const text = candidate?.content?.parts?.map(p => p.text).join('') || '';

        if (!text) {
          return sendJsonResponse(res, 200, {
            text: 'The AI returned an empty response. Please try again.',
            model: activeModel,
            empty: true
          });
        }

        return sendJsonResponse(res, 200, {
          text: text,
          model: activeModel
        });

      } catch (err) {
        console.error('Error processing AI request:', err.message);
        return sendJsonResponse(res, 500, { error: 'AI service is temporarily unavailable.' });
      }
    });
    return;
  }

  // Streaming chat endpoint via SSE
  if ((req.method === 'POST' || req.method === 'GET') && pathname === '/api/chat/stream') {
    let prompt = '';
    let history = [];

    if (req.method === 'GET') {
      prompt = (parsedUrl.query.prompt || '').toString().trim();
    } else {
      let body = '';
      await new Promise(resolve => {
        req.on('data', chunk => { body += chunk; });
        req.on('end', resolve);
      });
      try {
        const payload = JSON.parse(body || '{}');
        prompt = (payload.prompt || '').trim();
        history = Array.isArray(payload.history) ? payload.history : [];
      } catch (e) {
        // ignore
      }
    }

    if (!prompt && history.length === 0) {
      return sendJsonResponse(res, 400, { error: 'Prompt or history cannot be empty' });
    }

    if (!GEMINI_API_KEY) {
      return sendJsonResponse(res, 503, {
        error: 'AI service setup is incomplete: GEMINI_API_KEY is not configured on the server.'
      });
    }

    const contents = [];
    for (const item of history) {
      contents.push({
        role: item.role === 'model' ? 'model' : 'user',
        parts: [{ text: item.text }]
      });
    }
    if (prompt) {
      contents.push({
        role: 'user',
        parts: [{ text: prompt }]
      });
    }

    const requestBody = JSON.stringify({
      system_instruction: {
        parts: [{ text: SYSTEM_INSTRUCTION }]
      },
      contents: contents,
      generationConfig: {
        temperature: 0.7,
        topP: 0.95,
        maxOutputTokens: 2048
      }
    });

    let activeModel = PRIMARY_MODEL;
    let streamUrl = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(activeModel)}:streamGenerateContent?alt=sse&key=${encodeURIComponent(GEMINI_API_KEY)}`;

    try {
      let geminiRes = await fetch(streamUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: requestBody
      });

      // Fallback on 404 or 503
      if (!geminiRes.ok && (geminiRes.status === 404 || geminiRes.status === 503)) {
        console.warn(`Streaming: model ${activeModel} returned ${geminiRes.status}, falling back to ${FALLBACK_MODEL}`);
        activeModel = FALLBACK_MODEL;
        streamUrl = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(activeModel)}:streamGenerateContent?alt=sse&key=${encodeURIComponent(GEMINI_API_KEY)}`;
        geminiRes = await fetch(streamUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: requestBody
        });
      }

      if (!geminiRes.ok) {
        if (geminiRes.status === 429) {
          return sendJsonResponse(res, 429, {
            error: 'AI usage limit reached. Please try again later.'
          });
        }
        if (geminiRes.status === 503) {
          return sendJsonResponse(res, 503, {
            error: 'AI service is temporarily unavailable. Please try again in a moment.'
          });
        }
        return sendJsonResponse(res, geminiRes.status, {
          error: `AI service returned status HTTP ${geminiRes.status}`
        });
      }

      res.writeHead(200, {
        'Content-Type': 'text/event-stream; charset=utf-8',
        'Cache-Control': 'no-cache, no-transform',
        'Connection': 'keep-alive',
        'Access-Control-Allow-Origin': '*'
      });

      const reader = geminiRes.body.getReader();
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        const chunk = decoder.decode(value, { stream: true });
        res.write(chunk);
      }

      res.end();
    } catch (err) {
      console.error('Streaming error:', err.message);
      if (!res.headersSent) {
        sendJsonResponse(res, 500, { error: 'AI service is temporarily unavailable.' });
      } else {
        res.end();
      }
    }
    return;
  }

  sendJsonResponse(res, 404, { error: 'Endpoint not found' });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`NK APP Secure Backend running on port ${PORT}`);
  console.log(`Primary Model: ${PRIMARY_MODEL}, Fallback Model: ${FALLBACK_MODEL}`);
  console.log(`API Key configured: ${Boolean(GEMINI_API_KEY)}`);
});
