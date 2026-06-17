#!/usr/bin/env node
// Smoke-tests the adf4j wasm build with a handful of cases and asserts the Markdown output.
// Exits non-zero on any failure (used as the CI smoke test). Run: node test.mjs

import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import assert from 'node:assert/strict';
import { loadAdf4j } from './adf4j-wasm.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const adf4j = await loadAdf4j();
const expectedVersion = await readExpectedVersion();

let passed = 0;
function check(name, fn) {
  try {
    fn();
    passed++;
    console.log(`  ok   ${name}`);
  } catch (err) {
    console.error(`  FAIL ${name}\n       ${err.message}`);
    process.exitCode = 1;
  }
}

const doc = (content) => JSON.stringify({ version: 1, type: 'doc', content });
const para = (text) => ({ type: 'paragraph', content: [{ type: 'text', text }] });

console.log(`adf4j wasm v${adf4j.version()}\n`);

check('version is reported', () => {
  const version = adf4j.version();
  assert.match(version, /^\d+\.\d+\.\d+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?$/);
  if (expectedVersion) {
    assert.equal(version, expectedVersion);
  }
});

check('heading renders as ATX', () => {
  const md = adf4j.convert(doc([
    { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: 'Title' }] },
  ]));
  assert.equal(md.trim(), '## Title');
});

check('strong + paragraph', () => {
  const md = adf4j.convert(doc([
    {
      type: 'paragraph', content: [
        { type: 'text', text: 'hello ' },
        { type: 'text', text: 'world', marks: [{ type: 'strong' }] },
      ]
    },
  ]));
  assert.equal(md.trim(), 'hello **world**');
});

check('bullet list', () => {
  const md = adf4j.convert(doc([
    {
      type: 'bulletList', content: [
        { type: 'listItem', content: [para('one')] },
        { type: 'listItem', content: [para('two')] },
      ]
    },
  ]));
  assert.match(md, /[-*] +one/);
  assert.match(md, /[-*] +two/);
});

check('code block keeps language fence', () => {
  const md = adf4j.convert(doc([
    { type: 'codeBlock', attrs: { language: 'js' }, content: [{ type: 'text', text: 'a=1' }] },
  ]));
  assert.match(md, /```js[\s\S]*a=1[\s\S]*```/);
});

check('convertJson reports a clean (non-lossy) doc', () => {
  const r = adf4j.convertJson(doc([para('plain text')]));
  assert.equal(r.ok, true);
  assert.equal(r.lossy, false);
  assert.equal(r.body.trim(), 'plain text');
});

check('convertJson preserves quoted text in the body', () => {
  const r = adf4j.convertJson(doc([para('say "hi"')]));
  assert.equal(r.ok, true);
  assert.equal(r.body.trim(), 'say "hi"');
});

check('invalid JSON degrades gracefully (diagnostics, no crash)', () => {
  // The library does not throw on bad input; it returns an empty body plus error diagnostics.
  const r = adf4j.convertJson('{not valid json');
  assert.equal(r.ok, true);
  assert.equal(r.body, '');
  assert.ok(r.errors > 0, `expected error diagnostics, got ${JSON.stringify(r)}`);
});

await checkSampleFile();
async function checkSampleFile() {
  const adfJson = await readFile(resolve(here, 'sample.adf.json'), 'utf8');
  check('sample.adf.json converts and contains expected fragments', () => {
    const r = adf4j.convertJson(adfJson);
    assert.equal(r.ok, true);
    assert.match(r.body, /# Release notes/);
    assert.match(r.body, /\*\*WebAssembly\*\*/);
    assert.match(r.body, /```bash[\s\S]*node run\.mjs convert/);
  });
}

console.log(`\n${passed} checks passed${process.exitCode ? ', with failures' : ''}.`);

async function readExpectedVersion() {
  try {
    const properties = await readFile(
      resolve(
        here,
        '..',
        '..',
        '..',
        'target',
        'classes',
        'dev',
        'nthings',
        'adf4j',
        'wasm',
        'adf4j-wasm.properties',
      ),
      'utf8',
    );
    return properties.match(/^version=(.+)$/m)?.[1];
  } catch {
    return undefined;
  }
}
