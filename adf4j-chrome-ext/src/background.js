'use strict';

const MESSAGE_TYPE = 'adf4j.convert';
const READY_TIMEOUT_MS = 30_000;
const MAX_ATTACHMENTS = 5_000;
let readyTimeout;
let rejectReady;

const adf4jReady = new Promise((resolve, reject) => {
  rejectReady = reject;
  readyTimeout = setTimeout(() => {
    reject(new Error(`adf4j wasm did not signal ready within ${READY_TIMEOUT_MS}ms`));
  }, READY_TIMEOUT_MS);

  globalThis.__adf4jOnReady = () => {
    clearTimeout(readyTimeout);
    const api = globalThis.adf4j;
    if (!api || typeof api.convertJson !== 'function') {
      reject(new Error('adf4j wasm reported ready but exposed no convertJson().'));
      return;
    }
    resolve(api);
  };
});
adf4jReady.catch(() => {});

globalThis.__adf4jWasmPath = chrome.runtime.getURL('adf4j-wasm.js.wasm');
try {
  importScripts('adf4j-wasm.js');
} catch (error) {
  clearTimeout(readyTimeout);
  rejectReady(error);
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  const request = validateConversionRequest(message, sender);
  if (!request.handled) {
    return false;
  }

  if (!request.ok) {
    sendResponse({ ok: false, error: request.error });
    return false;
  }

  convert(request.adfJson, request.context).then(sendResponse, (error) => {
    sendResponse({ ok: false, error: error?.message || String(error) });
  });
  return true;
});

function validateConversionRequest(message, sender) {
  if (!message || message.type !== MESSAGE_TYPE) {
    return { handled: false };
  }

  if (!isConfluenceCloudWikiUrl(sender?.tab?.url)) {
    return {
      handled: true,
      ok: false,
      error: 'Conversion requests must originate from a Confluence Cloud wiki page.',
    };
  }

  if (typeof message.adfJson !== 'string' || !message.adfJson.trim()) {
    return { handled: true, ok: false, error: 'Missing ADF JSON payload.' };
  }

  const context = sanitizeConversionContext(message.context);
  if (!context.ok) {
    return { handled: true, ok: false, error: context.error };
  }

  return {
    handled: true,
    ok: true,
    adfJson: message.adfJson,
    context: context.value,
  };
}

function sanitizeConversionContext(context) {
  if (context === undefined || context === null) {
    return { ok: true, value: undefined };
  }
  if (!isPlainObject(context)) {
    return { ok: false, error: 'Invalid conversion context payload.' };
  }

  const keys = Object.keys(context);
  if (keys.some((key) => key !== 'attachments')) {
    return { ok: false, error: 'Invalid conversion context payload.' };
  }

  if (context.attachments === undefined) {
    return { ok: true, value: undefined };
  }
  if (!Array.isArray(context.attachments) || context.attachments.length > MAX_ATTACHMENTS) {
    return { ok: false, error: 'Invalid attachment metadata payload.' };
  }

  const attachments = [];
  for (const item of context.attachments) {
    if (!isPlainObject(item) || typeof item.fileId !== 'string' || !item.fileId.trim()) {
      return { ok: false, error: 'Invalid attachment metadata payload.' };
    }
    attachments.push({
      fileId: item.fileId.trim(),
      title: optionalString(item.title),
      mediaType: optionalString(item.mediaType),
      downloadUrl: optionalString(item.downloadUrl),
    });
  }

  return { ok: true, value: { attachments } };
}

async function convert(adfJson, context) {
  if (typeof adfJson !== 'string' || !adfJson.trim()) {
    return { ok: false, error: 'Missing ADF JSON payload.' };
  }

  const adf4j = await loadAdf4j();
  const result = adf4j.convertJson(adfJson, typeof context === 'object' ? context : undefined);
  if (!result?.ok) {
    return { ok: false, error: result?.error || 'adf4j conversion failed.' };
  }

  return {
    ok: true,
    markdown: String(result.body ?? ''),
    lossy: Boolean(result.lossy),
    warnings: Number(result.warnings ?? 0),
    errors: Number(result.errors ?? 0),
  };
}

function loadAdf4j() {
  if (globalThis.adf4j?.ready) {
    return Promise.resolve(globalThis.adf4j);
  }
  return adf4jReady;
}

function isConfluenceCloudWikiUrl(url) {
  let parsed;
  try {
    parsed = new URL(String(url));
  } catch {
    return false;
  }
  return (
    parsed.protocol === 'https:'
    && parsed.hostname.endsWith('.atlassian.net')
    && parsed.pathname.startsWith('/wiki/')
  );
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function optionalString(value) {
  return typeof value === 'string' ? value : '';
}

globalThis.Adf4jChromeExtBackground = Object.freeze({
  MESSAGE_TYPE,
  isConfluenceCloudWikiUrl,
  sanitizeConversionContext,
  validateConversionRequest,
});
