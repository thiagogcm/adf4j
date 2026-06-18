#!/usr/bin/env node
// Convert one ADF JSON document to Markdown using the adf4j wasm build.
//
//   node run.mjs [path-to-adf.json]
//
// Defaults to sample.adf.json. ADF is read with Node's own fs (the wasm image has no host
// filesystem access), then handed to the in-wasm converter as a plain string.

import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { loadAdf4j } from '../../npm/adf4j-wasm.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const inputPath = resolve(process.argv[2] ?? resolve(here, 'sample.adf.json'));

let adfJson;
try {
  adfJson = await readFile(inputPath, 'utf8');
} catch (err) {
  console.error(`cannot read ${inputPath}: ${err.code ?? err.message}`);
  process.exit(2);
}

// Running from the source tree: load the freshly built image under target/.
const targetDir = new URL('../../../target/', import.meta.url);
const adf4j = await loadAdf4j({
  imageUrl: new URL('adf4j-wasm.js', targetDir),
  wasmUrl: new URL('adf4j-wasm.js.wasm', targetDir),
});

console.error(`adf4j wasm v${adf4j.version()} — converting ${inputPath}`);
const result = adf4j.convertJson(adfJson);

if (!result.ok) {
  console.error(`conversion failed: ${result.error}`);
  process.exit(1);
}

process.stdout.write(result.body);
if (!result.body.endsWith('\n')) {
  process.stdout.write('\n');
}

console.error(`\n[lossy=${result.lossy} warnings=${result.warnings} errors=${result.errors}]`);
