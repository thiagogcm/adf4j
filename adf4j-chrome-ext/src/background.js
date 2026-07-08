'use strict';

const MESSAGE_TYPE = 'adf4j.convert';
const READY_TIMEOUT_MS = 30_000;
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
  if (!message || message.type !== MESSAGE_TYPE) {
    return false;
  }

  convert(message.adfJson, message.context).then(sendResponse, (error) => {
    sendResponse({ ok: false, error: error?.message || String(error) });
  });
  return true;
});

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
