// Loads the adf4j GraalVM Web Image (WebAssembly) and returns a small JS-native API. The image is a
// CommonJS script that, when evaluated, runs the Java `main`, which publishes
// `globalThis.adf4j = { convert, convertJson, version }` then calls `globalThis.__adf4jOnReady()`. We
// install that callback first, trigger evaluation with `require`, and resolve once it fires.

import { createRequire } from 'node:module';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const require = createRequire(import.meta.url);
const here = dirname(fileURLToPath(import.meta.url));

// Resolve the image: explicit override, then the Maven build output (in-repo), then a copy sitting
// next to this loader (the layout of the released adf4j-wasm-<ver>.zip bundle).
function resolveImage(explicit) {
  const candidates = [
    explicit,
    process.env.ADF4J_WASM_JS,
    resolve(here, '..', '..', '..', 'target', 'adf4j-wasm.js'),
    resolve(here, 'adf4j-wasm.js'),
  ].filter(Boolean);
  return candidates.find((p) => existsSync(p)) ?? candidates[candidates.length - 1];
}

let cached;

/**
 * @param {{ timeoutMs?: number, imagePath?: string }} [opts]
 * @returns {Promise<{ convert(json: string): string,
 *                     convertJson(json: string): object,
 *                     version(): string }>}
 */
export function loadAdf4j(opts = {}) {
  if (cached) {
    return cached;
  }
  const { timeoutMs = 30_000 } = opts;
  const imagePath = resolveImage(opts.imagePath);

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

    // Evaluating the web image runs `main` (asynchronously, via GraalVM.run().catch()).
    try {
      require(imagePath);
    } catch (err) {
      clearTimeout(timer);
      rej(new Error(`could not load wasm image at ${imagePath}: ${err.message}`));
    }
  });

  return cached;
}
