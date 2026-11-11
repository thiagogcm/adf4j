// Import order matters: the loader must set the globals that adf4j-wasm.js reads while evaluating.
import { adf4jReady } from './adf4j-loader.js';
import './adf4j-wasm.js';
import { conversionResponse, createListener } from './messaging.js';

chrome.runtime.onMessage.addListener(createListener(convert));

async function convert(adfJson, context) {
  // The image may have become ready after the loader timed out; use it if so.
  const adf4j = globalThis.adf4j?.ready ? globalThis.adf4j : await adf4jReady;
  return conversionResponse(adf4j.convert(adfJson, { context }));
}
