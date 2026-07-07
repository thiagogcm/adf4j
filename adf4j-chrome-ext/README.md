# adf4j-chrome-ext

Manifest V3 Chrome extension for copying Confluence Cloud pages as Markdown using the generated
`adf4j-wasm` package.

## Build

From the repository root:

```bash
just chrome-ext
```

That builds `adf4j-wasm`, assembles the local npm package under `dist/npm/adf4j-wasm`, runs the
extension tests, and writes the unpacked extension to `adf4j-chrome-ext/dist`.

If the wasm npm package is already assembled, use:

```bash
just chrome-ext-from-dist
```

The repository root owns the Node package configuration. Direct script equivalents are:

```bash
node --run chrome-ext:test
node --run chrome-ext:build
node --run chrome-ext:verify
```

## Load in Chrome

1. Open `chrome://extensions`.
2. Enable Developer mode.
3. Choose **Load unpacked**.
4. Select `adf4j-chrome-ext/dist`.

Open a Confluence Cloud page under `https://*.atlassian.net/wiki/*`. The extension adds a floating
`Copy as markdown` button when it can identify a page id. Clicking the button fetches the published
page body through Confluence REST v2 with `body-format=atlas_doc_format`, converts it in the
extension background worker, and writes the result to the clipboard.

## Scope

This first version supports Confluence Cloud only. It copies the published page body, not editor
draft state.
