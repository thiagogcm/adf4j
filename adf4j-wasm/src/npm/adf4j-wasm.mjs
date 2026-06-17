// Loads the adf4j GraalVM Web Image (WebAssembly) and returns a small JS-native API. The generated
// image script runs Java `main`, publishes `globalThis.adf4j = { convert, convertJson, version }`,
// then calls `globalThis.__adf4jOnReady()`. This wrapper installs that callback first and evaluates
// the generated image from Node.js or a browser.

const localImageUrl = new URL('./adf4j-wasm.js', import.meta.url);
const localWasmUrl = new URL('./adf4j-wasm.js.wasm', import.meta.url);
const sourceTreeImageUrl = new URL('../../target/adf4j-wasm.js', import.meta.url);
const sourceTreeWasmUrl = new URL('../../target/adf4j-wasm.js.wasm', import.meta.url);

let cached;

/**
 * @param {{ timeoutMs?: number,
 *           imagePath?: string,
 *           imageUrl?: string | URL,
 *           wasmPath?: string,
 *           wasmUrl?: string | URL }} [opts]
 * @returns {Promise<{ convert(json: string): string,
 *                     convertJson(json: string): object,
 *                     version(): string }>}
 */
export function loadAdf4j(opts = {}) {
  if (globalThis.adf4j?.ready) {
    return Promise.resolve(globalThis.adf4j);
  }
  if (cached) {
    return cached;
  }
  const { timeoutMs = 30_000 } = opts;
  const imageCandidates = resolveImageCandidates(opts);

  cached = new Promise((res, rej) => {
    const timer = setTimeout(
      () => rej(new Error(`adf4j wasm did not signal ready within ${timeoutMs}ms`)),
      timeoutMs,
    );

    // Java's signalReady() invokes this once the bridge functions are registered.
    globalThis.__adf4jOnReady = () => {
      clearTimeout(timer);
      const api = globalThis.adf4j;
      if (!api || typeof api.convert !== 'function') {
        rej(new Error('adf4j wasm reported ready but exposed no convert()'));
        return;
      }
      res(api);
    };

    loadFirstAvailableImage(imageCandidates).catch((err) => {
      clearTimeout(timer);
      cached = undefined;
      rej(err);
    });
  });

  return cached;
}

function resolveImageCandidates(opts) {
  return [
    imageCandidate(opts.imageUrl, opts.wasmUrl),
    imageCandidate(opts.imagePath, opts.wasmPath),
    imageCandidate(globalThis.process?.env?.ADF4J_WASM_JS),
    imageCandidate(localImageUrl, localWasmUrl),
    imageCandidate(sourceTreeImageUrl, sourceTreeWasmUrl),
  ].filter(Boolean);
}

function imageCandidate(image, wasm) {
  return image ? { image, wasm } : undefined;
}

async function loadFirstAvailableImage(candidates) {
  let lastError;
  for (const candidate of candidates) {
    try {
      await loadImage(candidate.image, candidate.wasm);
      return;
    } catch (err) {
      lastError = err;
    }
  }
  throw new Error(`could not load adf4j wasm image: ${lastError?.message ?? 'no candidates'}`);
}

function loadImage(candidate, wasm) {
  if (wasm) {
    globalThis.__adf4jWasmPath = runtimeWasmPath(wasm);
  }
  if (typeof document !== 'undefined' && document.createElement) {
    return loadBrowserScript(candidate);
  }
  return import(String(candidate));
}

function runtimeWasmPath(wasm) {
  if (!isNodeRuntime()) {
    return String(wasm);
  }
  try {
    const url = wasm instanceof URL ? wasm : new URL(String(wasm));
    if (url.protocol === 'file:') {
      return decodeURIComponent(url.pathname);
    }
  } catch {
    // Plain filesystem paths are already in the format Node's fs.readFile expects.
  }
  return String(wasm);
}

function isNodeRuntime() {
  return typeof process !== 'undefined' && process.versions?.node;
}

function loadBrowserScript(candidate) {
  return new Promise((res, rej) => {
    const script = document.createElement('script');
    script.src = String(candidate);
    script.async = true;
    script.onload = () => res();
    script.onerror = () => {
      script.remove();
      rej(new Error(`could not load browser script ${script.src}`));
    };
    document.head.append(script);
  });
}
