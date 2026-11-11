# adf4j-wasm

WebAssembly build of [adf4j](https://github.com/thiagogcm/adf4j), callable from Node.js, Bun, Deno, or a browser with no JVM. It converts Atlassian Document Format (ADF) JSON to GitHub-Flavored Markdown.

```bash
npm install @nthings.dev/adf4j-wasm
```

The same entry point works everywhere; `loadAdf4j()` detects the runtime and loads the matching assets. The first call boots the ~16 MB module once and caches it, so reuse the resolved API.

## Node.js, Bun, Deno

```js
import { loadAdf4j } from "@nthings.dev/adf4j-wasm";

const adf4j = await loadAdf4j();

const result = adf4j.convert(
  JSON.stringify({
    version: 1,
    type: "doc",
    content: [
      {
        type: "paragraph",
        content: [{ type: "text", text: "Hello from ADF" }],
      },
    ],
  }),
);

if (!result.ok) {
  throw new Error(result.error);
}
console.log(result.body);
```

CommonJS works too on Node 22+ and Bun (`const { loadAdf4j } = require('@nthings.dev/adf4j-wasm')`). Deno needs read access to the bundled assets: `deno run --allow-read --allow-env your-script.js`.

## Vite

Vite pre-bundles dependencies for dev, which rewrites the package's internal asset URLs and would stop the `.wasm` from loading. Exclude the package from pre-bundling. The package ships a one-line plugin that does exactly this:

```js
import { defineConfig } from "vite";
import adf4jWasm from "@nthings.dev/adf4j-wasm/vite";

export default defineConfig({
  plugins: [adf4jWasm()],
});
```

Or set it by hand instead of using the plugin:

```js
export default defineConfig({
  optimizeDeps: { exclude: ["@nthings.dev/adf4j-wasm"] },
});
```

Then call it from your app code. Vite emits the generated JavaScript and `.wasm` as build assets automatically:

```js
import { loadAdf4j } from "@nthings.dev/adf4j-wasm";

const adf4j = await loadAdf4j();
console.log(adf4j.convert(adfJsonString).body);
```

Both dev (`vite`) and production (`vite build`) work with no other configuration.

## Other bundlers

The package references its assets with static `new URL('./asset', import.meta.url)` expressions, so webpack 5, Rollup, esbuild, and Parcel emit and resolve them without extra configuration. If a bundler relocates the assets in a way the loader cannot find, point it at the served URLs:

```js
await loadAdf4j({ imageUrl: "/adf4j-wasm.js", wasmUrl: "/adf4j-wasm.js.wasm" });
```

To self-host the assets (for example by copying them into a `public/` directory), resolve them from the package's subpath exports:

```js
import imageUrl from "@nthings.dev/adf4j-wasm/adf4j-wasm.js?url";
import wasmUrl from "@nthings.dev/adf4j-wasm/adf4j-wasm.js.wasm?url";

await loadAdf4j({ imageUrl, wasmUrl });
```

In Node.js, pass file URLs for custom assets. `ADF4J_WASM_JS` and `ADF4J_WASM` environment variables also supply asset locations. An explicit `imageUrl` is authoritative; loader failures do not silently select a different bundled image.

## API

| Call | Result |
| --- | --- |
| `convert(json, config?)` | Conversion outcome with Markdown, source metadata, effects, and unresolved resources. |
| `analyze(json, config?)` | Source metadata and diagnostics without rendering. |
| `version()` | Package version. |

ADF input is a JSON string. Both operations expose `ok`, `hasWarnings`, and `diagnostics`. Failure includes `error`; failed conversion always has an empty body. TypeScript narrows the outcome on `ok`.

```js
const config = {
  markdown: { collapseHardBreaks: true, tableFallback: "gfm-empty-header" },
  context: {
    title: "Quarterly report",
    attachments: [
      {
        fileId: "file-1",
        title: "report.xlsx",
        downloadUrl: "https://example.com/downloads/report.xlsx",
      },
    ],
  },
};
const analysis = adf4j.analyze(adfJsonString, config);
if (!analysis.ok) throw new Error(analysis.error);
console.log(analysis.metadata.referencedFileIds);

const result = adf4j.convert(adfJsonString, config);
if (!result.ok) throw new Error(result.error);
console.log(result.body);
```

Omitted attachments mean the inventory is unknown; `[]` is authoritative. Unknown configuration keys and wrong field types return `INVALID_OPTIONS`. Java callbacks cannot cross the WASM boundary.

Conversion results also contain:

| Field | Meaning |
| --- | --- |
| `metadata` | Source references and outline, including attachment file IDs. |
| `effects` | Supported content, presentation, and structure changes, independently of severity. |
| `unresolved` | Array of `{ kind, id, path, reason, fallback }` for missing output dependencies. |
| `hasWarnings` | At least one `WARNING`; this does not indicate success or losslessness. |
| `diagnostics` | Issues with stable codes, severity, messages, JSON Pointer paths, and structured details. |

The package exports `conversion-options.schema.json` and `operation-result.schema.json` for editors and automated consumers. Java exception causes never appear in portable output.
