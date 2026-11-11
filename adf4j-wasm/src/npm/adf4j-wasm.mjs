// Loads the GraalVM Web Image. The image publishes globalThis.adf4j, then calls __adf4jOnReady.

// Kept as literal new URL(...) so bundlers can emit and rewrite them.
const localImageUrl = new URL('./adf4j-wasm.js', import.meta.url);
const localWasmUrl = new URL('./adf4j-wasm.js.wasm', import.meta.url);

let cached;

/**
 * @param {{ timeoutMs?: number,
 *           imageUrl?: string | URL,
 *           wasmUrl?: string | URL }} [opts]
 * @returns {Promise<{ convert(json: string, config?: object | string): object,
 *                     analyze(json: string, config?: object | string): object,
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
      () => { cached = undefined; rej(new Error(`adf4j wasm did not signal ready within ${timeoutMs}ms`)); },
      timeoutMs,
    );

    // Invoked by the image once globalThis.adf4j is installed.
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
  const env = globalThis.process?.env ?? {};
  if (opts.imageUrl) return [imageCandidate(opts.imageUrl, opts.wasmUrl)];
  return [
    imageCandidate(env.ADF4J_WASM_JS, env.ADF4J_WASM),
    imageCandidate(localImageUrl, opts.wasmUrl ?? localWasmUrl),
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
    // Not a URL: already a plain filesystem path.
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
