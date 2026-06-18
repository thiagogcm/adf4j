#!/usr/bin/env node
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const [version = '', outputArg = 'dist/npm/adf4j-wasm'] = process.argv.slice(2);
const output = resolve(root, outputArg);

if (!version) {
  throw new Error('usage: node adf4j-wasm/scripts/prepare-npm-package.mjs <version> [output-dir]');
}

await mkdir(output, { recursive: true });

await copyFile(resolve(root, 'adf4j-wasm/src/npm/package.json'), resolve(output, 'package.json'));
await copyFile(resolve(root, 'adf4j-wasm/src/npm/README.md'), resolve(output, 'README.md'));
await copyFile(resolve(root, 'adf4j-wasm/src/npm/adf4j-wasm.mjs'), resolve(output, 'adf4j-wasm.mjs'));
await copyFile(resolve(root, 'adf4j-wasm/src/npm/adf4j-wasm.d.ts'), resolve(output, 'adf4j-wasm.d.ts'));
await copyFile(resolve(root, 'adf4j-wasm/src/npm/vite.mjs'), resolve(output, 'vite.mjs'));
await copyFile(resolve(root, 'adf4j-wasm/src/npm/vite.d.ts'), resolve(output, 'vite.d.ts'));
await copyFile(resolve(root, 'adf4j-wasm/target/adf4j-wasm.js.wasm'), resolve(output, 'adf4j-wasm.js.wasm'));
await copyFile(resolve(root, 'LICENSE'), resolve(output, 'LICENSE'));

const packageJsonPath = resolve(output, 'package.json');
const packageJson = JSON.parse(await readFile(packageJsonPath, 'utf8'));
packageJson.version = version;
await writeFile(packageJsonPath, `${JSON.stringify(packageJson, null, 2)}\n`);

const imagePath = resolve(root, 'adf4j-wasm/target/adf4j-wasm.js');
const image = await readFile(imagePath, 'utf8');
const needle = 'const config = new GraalVM.Config();\nGraalVM.run(load_cmd_args(),config).catch(console.error);';
const replacement = `const config = new GraalVM.Config();
if (typeof globalThis.__adf4jWasmPath === "string") {
    config.wasm_path = globalThis.__adf4jWasmPath;
}
GraalVM.run(load_cmd_args(),config).catch(console.error);`;

if (!image.includes(needle)) {
  throw new Error('could not patch generated GraalVM loader: bootstrap marker not found');
}

await writeFile(resolve(output, 'adf4j-wasm.js'), image.replace(needle, replacement));
