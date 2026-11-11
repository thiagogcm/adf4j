#!/usr/bin/env node
// Converts one ADF JSON file to Markdown with the wasm build. Usage: node run.mjs [path].
// Node reads the file, since the wasm image has no filesystem access.

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

// Source-tree run: use the image built under target/.
const targetDir = new URL('../../../target/', import.meta.url);
const adf4j = await loadAdf4j({
  imageUrl: new URL('adf4j-wasm.js', targetDir),
  wasmUrl: new URL('adf4j-wasm.js.wasm', targetDir),
});

console.error(`adf4j wasm v${adf4j.version()} — converting ${inputPath}`);
const result = adf4j.convert(adfJson);

if (!result.ok) {
  console.error(`conversion failed: ${result.error}`);
  process.exit(1);
}

process.stdout.write(result.body);
if (!result.body.endsWith('\n')) {
  process.stdout.write('\n');
}

console.error(`\n[hasWarnings=${result.hasWarnings} diagnostics=${result.diagnostics.length}]`);
