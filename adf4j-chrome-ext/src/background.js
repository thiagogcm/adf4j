// ES module service worker. Import order matters: the loader installs the wasm-path and
// ready-handshake globals the image reads while it evaluates.
import { adf4jReady } from './adf4j-loader.js';
import './adf4j-wasm.js';
import { conversionResponse, createListener } from './messaging.js';

chrome.runtime.onMessage.addListener(createListener(convert));

async function convert(adfJson, context) {
  // Recovers if the image signaled ready only after the loader's timeout already fired.
  const adf4j = globalThis.adf4j?.ready ? globalThis.adf4j : await adf4jReady;
  return conversionResponse(adf4j.convert(adfJson, context));
}
