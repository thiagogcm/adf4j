import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const manifest = JSON.parse(
  await readFile(new URL('../manifest.json', import.meta.url), 'utf8'),
);

test('manifest keeps first-release metadata and scoped permissions', () => {
  assert.equal(manifest.manifest_version, 3);
  assert.equal(manifest.version, '1.1.0');
  assert.ok(manifest.description.length <= 132);
  assert.deepEqual(manifest.permissions, ['clipboardWrite']);
  assert.deepEqual(manifest.host_permissions, ['https://*.atlassian.net/wiki/*']);
  assert.deepEqual(manifest.content_scripts[0].matches, ['https://*.atlassian.net/wiki/*']);
  assert.equal(JSON.stringify(manifest).includes('<all_urls>'), false);
});

test('manifest declares all packaged PNG icon sizes', async () => {
  assert.deepEqual(manifest.icons, {
    16: 'icons/icon16.png',
    32: 'icons/icon32.png',
    48: 'icons/icon48.png',
    128: 'icons/icon128.png',
  });

  for (const [size, path] of Object.entries(manifest.icons)) {
    const icon = await readFile(new URL(`../assets/${path}`, import.meta.url));
    assert.deepEqual([...icon.subarray(0, 8)], [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
    assert.equal(icon.readUInt32BE(16), Number(size));
    assert.equal(icon.readUInt32BE(20), Number(size));
  }
});

test('manifest CSP stays local-only except bundled WASM support', () => {
  assert.equal(
    manifest.content_security_policy.extension_pages,
    "script-src 'self' 'wasm-unsafe-eval'; object-src 'self';",
  );
  assert.doesNotMatch(manifest.content_security_policy.extension_pages, /https?:|data:|blob:/);
});

test('store listing summary is within Chrome Web Store limit', async () => {
  const listing = await readFile(new URL('../STORE_LISTING.md', import.meta.url), 'utf8');
  const summary = listing.match(/^Summary: (.+)$/m)?.[1];

  assert.ok(summary, 'STORE_LISTING.md must include a Summary field');
  assert.ok(summary.length <= 132, `summary is ${summary.length} characters`);
});
