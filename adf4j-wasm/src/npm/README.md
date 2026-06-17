# adf4j-wasm

WebAssembly build of [adf4j](https://github.com/thiagogcm/adf4j), callable from Node.js or a browser with no JVM.

```bash
npm install @nthings.dev/adf4j-wasm
```

## Node.js

```js
import { loadAdf4j } from '@nthings.dev/adf4j-wasm';

const adf4j = await loadAdf4j();

const markdown = adf4j.convert(JSON.stringify({
  version: 1,
  type: 'doc',
  content: [
    {
      type: 'paragraph',
      content: [{ type: 'text', text: 'Hello from ADF' }],
    },
  ],
}));

console.log(markdown);
```

`convert(json)` returns Markdown. `convertJson(json)` returns a JS object with `ok`, `lossy`,
`warnings`, `errors`, and `body` fields.

## Browser bundlers

```js
import { loadAdf4j } from '@nthings.dev/adf4j-wasm';

const adf4j = await loadAdf4j();
console.log(adf4j.convert(adfJsonString));
```

The package uses static `new URL(..., import.meta.url)` asset references so bundlers can emit the
generated JavaScript and WebAssembly files. If you need to load generated assets from custom URLs,
pass `loadAdf4j({ imageUrl, wasmUrl })`; in Node.js you can also pass `imagePath`/`wasmPath` or set
`ADF4J_WASM_JS`.
