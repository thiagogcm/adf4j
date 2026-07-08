# adf4j-wasm

A GraalVM **Web Image** (WebAssembly) build of adf4j, callable directly from JavaScript/Node.

## Why a separate module

GraalVM's wasm backend has **no stdin and only an in-memory filesystem**, so the `adf4j-cli` stdin/file contract can't be driven from a JS host. This module instead exposes plain `string -> string` functions via the `@JS` helper pattern (the supported stand-in while `@JS.Export` is unimplemented in GraalVM 25). See `src/main/java/.../WasmBridge.java`.

## Building

This module is **excluded from the default reactor** and only builds under the `-Pwasm` profile, which requires an **Oracle GraalVM JDK 25** (for the `svm-wasm` tool and the `org.graalvm.webimage.api` system module) and `wasm-as` from **Binaryen ≥ 119** on the `PATH`.

```bash
# JAVA_HOME must point at an Oracle GraalVM JDK 25
./mvnw -Pwasm -pl adf4j-wasm -am package -DskipTests
```

Outputs:

```bash
target/adf4j-wasm.js        # generated JS loader
target/adf4j-wasm.js.wasm   # the compiled module (~16 MB)
```

## Using it from JavaScript

```bash
npm install @nthings.dev/adf4j-wasm
```

One entry point covers Node.js, Bun, Deno, and the browser; `loadAdf4j()` detects the runtime.

```js
import { loadAdf4j } from "@nthings.dev/adf4j-wasm";

const adf4j = await loadAdf4j();
adf4j.version(); // adf4j version string
adf4j.convert(adfJsonString); // -> Markdown string
adf4j.convertJson(adfJsonString);
// -> { ok, lossy, warnings, errors, body }
```

In Vite, add the shipped plugin (or set `optimizeDeps.exclude: ['@nthings.dev/adf4j-wasm']`) so the `.wasm` survives dependency pre-bundling:

```js
import adf4jWasm from "@nthings.dev/adf4j-wasm/vite";
export default defineConfig({ plugins: [adf4jWasm()] });
```

Consumption details (Vite, other bundlers, Bun, Deno, CommonJS, self-hosting assets) live in the published package README at [src/npm/README.md](src/npm/README.md).

The package uses static `new URL(..., import.meta.url)` asset references so bundlers emit the generated JavaScript and WebAssembly files.

Override the asset locations with `loadAdf4j({ imageUrl, wasmUrl })`, `loadAdf4j({ imagePath, wasmPath })`, or the `ADF4J_WASM_JS` / `ADF4J_WASM` environment variables in Node.js.

## Smoke test / example consumer

`src/test/js/` is a runnable Node consumer, used as the CI smoke test:

```bash
node adf4j-wasm/src/test/js/test.mjs              # assertions over the built wasm
node adf4j-wasm/src/test/js/run.mjs [doc.json]    # convert a file to Markdown
```

CI (`.github/workflows/build-and-test.yml`, job `build-wasm`) builds the module and runs `test.mjs` against `target/` on every push/PR, then uploads the `.js`/`.wasm` bundle.
