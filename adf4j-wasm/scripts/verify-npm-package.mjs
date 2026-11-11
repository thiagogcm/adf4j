#!/usr/bin/env node
// Verifies the assembled npm package (default dist/npm/adf4j-wasm): exports, declared files,
// Vite plugin, conversion, and no source-tree-only paths.
//
//   node adf4j-wasm/scripts/verify-npm-package.mjs [package-dir]

import { readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';

const pkgDir = resolve(process.argv[2] ?? 'dist/npm/adf4j-wasm');
const fileUrl = (name) => pathToFileURL(resolve(pkgDir, name)).href;
const pkg = JSON.parse(await readFile(resolve(pkgDir, 'package.json'), 'utf8'));

// Exports must resolve for every condition and expose the raw assets for self-hosting.
const root = pkg.exports['.'];
assert.equal(root.import, './adf4j-wasm.mjs', 'exports["."].import');
assert.equal(root.require, './adf4j-wasm.mjs', 'exports["."].require');
assert.equal(root.default, './adf4j-wasm.mjs', 'exports["."].default');
assert.equal(root.types, './adf4j-wasm.d.ts', 'exports["."].types');
assert.equal(pkg.exports['./vite'].default, './vite.mjs', 'exports["./vite"]');
for (const asset of ['./adf4j-wasm.js', './adf4j-wasm.js.wasm', './package.json']) {
  assert.equal(pkg.exports[asset], asset, `exports["${asset}"]`);
}

for (const file of pkg.files) {
  assert.ok(existsSync(resolve(pkgDir, file)), `declared file missing: ${file}`);
}

// The shipped loader must not reference source-tree-only ../../target/ paths.
const loaderSource = await readFile(resolve(pkgDir, 'adf4j-wasm.mjs'), 'utf8');
assert.ok(!loaderSource.includes('../../target'), 'loader references ../../target');

const { default: adf4jWasm } = await import(fileUrl('vite.mjs'));
const plugin = adf4jWasm();
assert.equal(plugin.name, 'adf4j-wasm', 'plugin name');
assert.deepEqual(plugin.config().optimizeDeps.exclude, [pkg.name], 'plugin exclude');

const { loadAdf4j } = await import(fileUrl('adf4j-wasm.mjs'));
const adf4j = await loadAdf4j({
  imageUrl: fileUrl('adf4j-wasm.js'),
  wasmUrl: fileUrl('adf4j-wasm.js.wasm'),
});
const doc = JSON.stringify({
  version: 1,
  type: 'doc',
  content: [{ type: 'paragraph', content: [{ type: 'text', text: 'npm package' }] }],
});
const result = adf4j.convert(doc);
assert.equal(result.ok, true, 'convert ok');
assert.equal(result.body.trim(), 'npm package', 'convert body');

console.log(`ok  ${pkg.name}@${pkg.version} verified at ${pkgDir}`);
