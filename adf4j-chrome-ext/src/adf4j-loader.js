// Must run before adf4j-wasm.js: the image reads __adf4jWasmPath and calls __adf4jOnReady while it evaluates.
const READY_TIMEOUT_MS = 30_000;

globalThis.__adf4jWasmPath = chrome.runtime.getURL('adf4j-wasm.js.wasm');

export const adf4jReady = new Promise((resolve, reject) => {
  const timer = setTimeout(() => {
    reject(new Error(`adf4j wasm did not signal ready within ${READY_TIMEOUT_MS}ms`));
  }, READY_TIMEOUT_MS);

  globalThis.__adf4jOnReady = () => {
    clearTimeout(timer);
    const api = globalThis.adf4j;
    if (!api || typeof api.convert !== 'function') {
      reject(new Error('adf4j wasm reported ready but exposed no convert().'));
      return;
    }
    resolve(api);
  };
});
adf4jReady.catch(() => {});
