// Must be evaluated before adf4j-wasm.js: the image reads __adf4jWasmPath as it evaluates,
// and __adf4jOnReady is its only ready handshake.
const READY_TIMEOUT_MS = 30_000;

globalThis.__adf4jWasmPath = chrome.runtime.getURL('adf4j-wasm.js.wasm');

export const adf4jReady = new Promise((resolve, reject) => {
  const timer = setTimeout(() => {
    reject(new Error(`adf4j wasm did not signal ready within ${READY_TIMEOUT_MS}ms`));
  }, READY_TIMEOUT_MS);

  globalThis.__adf4jOnReady = () => {
    clearTimeout(timer);
    const api = globalThis.adf4j;
    if (!api || typeof api.convertJson !== 'function') {
      reject(new Error('adf4j wasm reported ready but exposed no convertJson().'));
      return;
    }
    resolve(api);
  };
});
adf4jReady.catch(() => {});
