# adf4j-wasm

A GraalVM **Web Image** (WebAssembly) build of adf4j, callable directly from JavaScript/Node.

It compiles `WasmBridge` — a thin entry point that publishes the adf4j converter onto `globalThis`
— to a `.wasm` module plus a JS loader, so a JS host can convert ADF JSON to Markdown in-process,
with no JVM.

## Why a separate module (and not the CLI)

GraalVM's wasm backend has **no stdin and only an in-memory filesystem**, so the `adf4j-cli`
stdin/file contract can't be driven from a JS host. This module instead exposes plain
`string -> string` functions via the `@JS` helper pattern (the supported stand-in while
`@JS.Export` is unimplemented in GraalVM 25). See `src/main/java/.../WasmBridge.java`.

## Building

This module is **excluded from the default reactor** and only builds under the `-Pwasm` profile,
which requires an **Oracle GraalVM JDK 25** (for the `svm-wasm` tool and the
`org.graalvm.webimage.api` system module) and `wasm-as` from **Binaryen ≥ 119** on the `PATH`.
A plain `mvn install` on any other JDK is unaffected — it never sees this module.

```bash
# JAVA_HOME must point at an Oracle GraalVM JDK 25
./mvnw -Pwasm -pl adf4j-wasm -am package -DskipTests
```

Outputs:

```
target/adf4j-wasm.js        # JS loader (CommonJS)
target/adf4j-wasm.js.wasm   # the compiled module (~16 MB)
```

## Using it from Node

```js
import { loadAdf4j } from './adf4j-wasm.mjs';   // see src/test/js/

const adf4j = await loadAdf4j();                // loads target/adf4j-wasm.js by default
adf4j.version();                                // "1.0.0-SNAPSHOT"
adf4j.convert(adfJsonString);                   // -> Markdown string
adf4j.convertJson(adfJsonString);
// -> { ok, lossy, warnings, errors, body }
```

The loader resolves `../../../target/adf4j-wasm.js`; override with the `ADF4J_WASM_JS`
environment variable (or `loadAdf4j({ imagePath })`).

## Smoke test / example consumer

`src/test/js/` is a runnable Node consumer, used as the CI smoke test:

```bash
node adf4j-wasm/src/test/js/test.mjs              # assertions over the built wasm
node adf4j-wasm/src/test/js/run.mjs [doc.json]    # convert a file to Markdown
```

CI (`.github/workflows/build-and-test.yml`, job `build-wasm`) builds the module and runs
`test.mjs` against `target/` on every push/PR, then uploads the `.js`/`.wasm` bundle.
