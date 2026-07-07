#!/usr/bin/env node
import { cp, mkdir, readFile, rm } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = resolve(projectRoot, '..');
const outputDir = resolve(projectRoot, 'dist');
const wasmPackageDir = resolve(repoRoot, 'dist/npm/adf4j-wasm');

const extensionFiles = [
  ['manifest.json', 'manifest.json'],
  ['src/background.js', 'background.js'],
  ['src/confluence.js', 'confluence.js'],
  ['src/content.js', 'content.js'],
  ['src/styles.css', 'styles.css'],
  ['README.md', 'README.md'],
];

const wasmFiles = ['adf4j-wasm.js', 'adf4j-wasm.js.wasm'];

await verifyManifest();
verifyWasmAssets();

await rm(outputDir, { force: true, recursive: true });
await mkdir(outputDir, { recursive: true });

for (const [source, target] of extensionFiles) {
  await cp(resolve(projectRoot, source), resolve(outputDir, target), { recursive: true });
}

for (const file of wasmFiles) {
  await cp(resolve(wasmPackageDir, file), resolve(outputDir, file));
}

const wasmLicense = resolve(wasmPackageDir, 'LICENSE');
if (existsSync(wasmLicense)) {
  await cp(wasmLicense, resolve(outputDir, 'LICENSE'));
}

console.log(`ok  Chrome extension assembled at ${outputDir}`);

async function verifyManifest() {
  const manifestPath = resolve(projectRoot, 'manifest.json');
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
  const contentScripts = manifest.content_scripts?.[0]?.js ?? [];
  for (const file of [manifest.background?.service_worker, ...contentScripts].filter(Boolean)) {
    const sourcePath = resolve(projectRoot, 'src', file);
    if (!existsSync(sourcePath)) {
      throw new Error(`manifest references missing source file: src/${file}`);
    }
  }
}

function verifyWasmAssets() {
  for (const file of wasmFiles) {
    const path = resolve(wasmPackageDir, file);
    if (!existsSync(path)) {
      throw new Error(
        `Missing ${path}. Run \`just wasm-npm\` first, or use \`just chrome-ext\` from the repository root.`,
      );
    }
  }
}
