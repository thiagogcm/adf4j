#!/usr/bin/env node
import { execFile as execFileCallback } from 'node:child_process';
import { existsSync } from 'node:fs';
import { cp, mkdir, readFile, readdir, rm } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = resolve(projectRoot, '..');
const outputDir = resolve(projectRoot, 'dist');
const zipPath = resolve(projectRoot, 'adf4j-copy-as-markdown.zip');
const wasmPackageDir = resolve(repoRoot, 'dist/npm/adf4j-wasm');
const execFile = promisify(execFileCallback);
const iconSizes = ['16', '32', '48', '128'];

const extensionFiles = [
  [resolve(projectRoot, 'manifest.json'), 'manifest.json'],
  [resolve(projectRoot, 'src/background.js'), 'background.js'],
  [resolve(projectRoot, 'src/confluence.js'), 'confluence.js'],
  [resolve(projectRoot, 'src/content.js'), 'content.js'],
  [resolve(projectRoot, 'src/styles.css'), 'styles.css'],
  [resolve(repoRoot, 'LICENSE'), 'LICENSE'],
];

const wasmFiles = ['adf4j-wasm.js', 'adf4j-wasm.js.wasm'].map((file) => [
  resolve(wasmPackageDir, file),
  file,
]);
const iconFiles = iconSizes.map((size) => [
  resolve(projectRoot, `assets/icons/icon${size}.png`),
  `icons/icon${size}.png`,
]);
const optionalNotice = resolve(repoRoot, 'NOTICE');
const packageFiles = [...extensionFiles, ...wasmFiles, ...iconFiles];
if (existsSync(optionalNotice)) {
  packageFiles.push([optionalNotice, 'NOTICE']);
}
const expectedPackageFiles = packageFiles.map(([, target]) => target).sort();

const manifest = await verifyManifest();
verifyWasmAssets();
await verifyIconAssets(manifest);
await assertNoRemoteScriptReferences();

await rm(outputDir, { force: true, recursive: true });
await rm(zipPath, { force: true });
await mkdir(outputDir, { recursive: true });

for (const [source, target] of extensionFiles) {
  await mkdir(dirname(resolve(outputDir, target)), { recursive: true });
  await cp(source, resolve(outputDir, target), { recursive: true });
}

for (const [source, target] of [...wasmFiles, ...iconFiles]) {
  await mkdir(dirname(resolve(outputDir, target)), { recursive: true });
  await cp(source, resolve(outputDir, target));
}

if (existsSync(optionalNotice)) {
  await cp(optionalNotice, resolve(outputDir, 'NOTICE'));
}

await validatePackageContents(outputDir);
await createWebStoreZip();
await validateWebStoreZip();

console.log(`ok  Chrome extension assembled at ${outputDir}`);
console.log(`ok  Chrome Web Store package written to ${zipPath}`);

async function verifyManifest() {
  const manifestPath = resolve(projectRoot, 'manifest.json');
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));

  assert(manifest.manifest_version === 3, 'manifest_version must be 3');
  assert(manifest.version === '1.1.0', 'manifest version must remain 1.1.0 until a store update');
  assert(typeof manifest.name === 'string' && manifest.name.trim(), 'manifest name is required');
  assert(
    typeof manifest.description === 'string' && manifest.description.length <= 132,
    'manifest description must be 132 characters or fewer',
  );
  assertArrayEquals(manifest.permissions, ['clipboardWrite'], 'manifest permissions');
  assertArrayEquals(
    manifest.host_permissions,
    ['https://*.atlassian.net/wiki/*'],
    'manifest host_permissions',
  );
  assert(!JSON.stringify(manifest).includes('<all_urls>'), 'manifest must not request <all_urls>');
  assert(!JSON.stringify(manifest).includes('*://*/*'), 'manifest must not request all hosts');

  assert(manifest.background?.service_worker === 'background.js', 'unexpected service worker path');
  assertPackageTarget(manifest.background.service_worker, 'background service worker');

  const contentScripts = manifest.content_scripts ?? [];
  assert(contentScripts.length === 1, 'manifest must have exactly one content script entry');
  assertArrayEquals(
    contentScripts[0].matches,
    ['https://*.atlassian.net/wiki/*'],
    'content script matches',
  );
  assertArrayEquals(
    contentScripts[0].js,
    ['confluence.js', 'content.js'],
    'content script JavaScript files',
  );
  assertArrayEquals(contentScripts[0].css, ['styles.css'], 'content script CSS files');
  assert(contentScripts[0].run_at === 'document_idle', 'content script run_at must be document_idle');
  for (const file of [...contentScripts[0].js, ...contentScripts[0].css]) {
    assertPackageTarget(file, 'content script file');
  }

  const csp = manifest.content_security_policy?.extension_pages;
  assert(
    csp === "script-src 'self' 'wasm-unsafe-eval'; object-src 'self';",
    'extension CSP must stay local-only except wasm-unsafe-eval',
  );

  for (const size of iconSizes) {
    const path = manifest.icons?.[size];
    assert(path === `icons/icon${size}.png`, `manifest icon ${size} must use icons/icon${size}.png`);
    assertPackageTarget(path, `manifest icon ${size}`);
  }

  return manifest;
}

function verifyWasmAssets() {
  for (const [source] of wasmFiles) {
    if (!existsSync(source)) {
      throw new Error(
        `Missing ${source}. Run \`just wasm-npm\` first, or use \`just chrome-ext\` from the repository root.`,
      );
    }
  }
}

async function verifyIconAssets(manifest) {
  for (const size of iconSizes) {
    const [source] = iconFiles.find(([, target]) => target === manifest.icons[size]);
    if (!existsSync(source)) {
      throw new Error(`Missing icon asset: ${source}`);
    }
    const png = await readFile(source);
    assert(
      png.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])),
      `${source} must be a PNG file`,
    );
    assert(png.readUInt32BE(16) === Number(size), `${source} width must be ${size}px`);
    assert(png.readUInt32BE(20) === Number(size), `${source} height must be ${size}px`);
  }
}

async function assertNoRemoteScriptReferences() {
  const remoteScriptPattern =
    /\b(?:importScripts|import)\s*\(\s*['"]https?:\/\/|(?:script|worker)\.src\s*=\s*['"]https?:\/\//i;
  for (const [source, target] of packageFiles) {
    if (!target.endsWith('.js') && !target.endsWith('.json') && !target.endsWith('.css')) {
      continue;
    }
    const sourceText = await readFile(source, 'utf8');
    if (remoteScriptPattern.test(sourceText)) {
      throw new Error(`Remote script reference found in package file: ${target}`);
    }
  }
}

async function validatePackageContents(root) {
  const actualFiles = await collectRelativeFiles(root);
  assertArrayEquals(actualFiles.sort(), expectedPackageFiles, 'extension package contents');
}

async function createWebStoreZip() {
  await requireExecutable('zip', ['-v']);
  await execFile('zip', ['-X', '-q', zipPath, ...expectedPackageFiles], { cwd: outputDir });
}

async function validateWebStoreZip() {
  await requireExecutable('unzip', ['-v']);
  const { stdout } = await execFile('unzip', ['-Z1', zipPath]);
  const entries = stdout.split(/\r?\n/).filter(Boolean).sort();
  assert(entries.includes('manifest.json'), 'ZIP must contain manifest.json at its root');
  assertArrayEquals(entries, expectedPackageFiles, 'Chrome Web Store ZIP contents');
}

async function collectRelativeFiles(root, dir = root) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const path = resolve(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...await collectRelativeFiles(root, path));
      continue;
    }
    if (entry.isFile()) {
      files.push(path.slice(root.length + 1).replaceAll('\\', '/'));
    }
  }
  return files;
}

async function requireExecutable(command, args) {
  try {
    await execFile(command, args);
  } catch (error) {
    if (error?.code === 'ENOENT') {
      throw new Error(`Missing required executable: ${command}`);
    }
  }
}

function assertPackageTarget(path, label) {
  assert(
    expectedPackageFiles.includes(path) && !path.includes('://') && !path.startsWith('/'),
    `${label} must reference a packaged extension file`,
  );
}

function assertArrayEquals(actual, expected, label) {
  assert(Array.isArray(actual), `${label} must be an array`);
  assert(
    actual.length === expected.length && actual.every((item, index) => item === expected[index]),
    `${label} must be exactly ${JSON.stringify(expected)}`,
  );
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}
