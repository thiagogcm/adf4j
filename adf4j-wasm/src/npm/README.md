# adf4j-wasm

WebAssembly build of [adf4j](https://github.com/thiagogcm/adf4j), callable from Node.js, Bun, Deno,
or a browser with no JVM. It converts Atlassian Document Format (ADF) JSON to GitHub-Flavored
Markdown.

```bash
npm install @nthings.dev/adf4j-wasm
```

The same entry point works everywhere; `loadAdf4j()` detects the runtime and loads the matching
assets. The first call boots the ~16 MB module once and caches it, so reuse the resolved API.

## Node.js, Bun, Deno

```js
import { loadAdf4j } from '@nthings.dev/adf4j-wasm';

const adf4j = await loadAdf4j();

const markdown = adf4j.convert(JSON.stringify({
  version: 1,
  type: 'doc',
  content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Hello from ADF' }] }],
}));

console.log(markdown);
```

CommonJS works too on Node 22+ and Bun (`const { loadAdf4j } = require('@nthings.dev/adf4j-wasm')`).
Deno needs read access to the bundled assets: `deno run --allow-read --allow-env your-script.js`.

## Vite

Vite pre-bundles dependencies for dev, which rewrites the package's internal asset URLs and would
stop the `.wasm` from loading. Exclude the package from pre-bundling. The package ships a one-line
plugin that does exactly this:

```js
import { defineConfig } from 'vite';
import adf4jWasm from '@nthings.dev/adf4j-wasm/vite';

export default defineConfig({
  plugins: [adf4jWasm()],
});
```

Or set it by hand instead of using the plugin:

```js
export default defineConfig({
  optimizeDeps: { exclude: ['@nthings.dev/adf4j-wasm'] },
});
```

Then call it from your app code. Vite emits the generated JavaScript and `.wasm` as build assets
automatically:

```js
import { loadAdf4j } from '@nthings.dev/adf4j-wasm';

const adf4j = await loadAdf4j();
console.log(adf4j.convert(adfJsonString));
```

Both dev (`vite`) and production (`vite build`) work with no other configuration.

## Other bundlers

The package references its assets with static `new URL('./asset', import.meta.url)` expressions, so
webpack 5, Rollup, esbuild, and Parcel emit and resolve them without extra configuration. If a bundler
relocates the assets in a way the loader cannot find, point it at the served URLs:

```js
await loadAdf4j({ imageUrl: '/adf4j-wasm.js', wasmUrl: '/adf4j-wasm.js.wasm' });
```

To self-host the assets (for example by copying them into a `public/` directory), resolve them from
the package's subpath exports:

```js
import imageUrl from '@nthings.dev/adf4j-wasm/adf4j-wasm.js?url';
import wasmUrl from '@nthings.dev/adf4j-wasm/adf4j-wasm.js.wasm?url';

await loadAdf4j({ imageUrl, wasmUrl });
```

In Node.js you can also pass `imagePath`/`wasmPath` (filesystem paths) or set the `ADF4J_WASM_JS` and
`ADF4J_WASM` environment variables.

## API

| Call                | Returns                                                                    |
| ------------------- | -------------------------------------------------------------------------- |
| `convert(json)`     | Markdown string.                                                           |
| `convertJson(json)` | `{ ok, lossy, warnings, errors, body }`, or `{ ok: false, error }` on a hard failure. |
| `version()`         | The adf4j version string.                                                  |

`json` is the ADF document as a JSON **string**. Invalid input does not throw; `convertJson` reports
it through `errors` and an empty `body`.
