#!/usr/bin/env node
// Smoke-tests the wasm build; exits non-zero on failure (CI smoke test).

import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import assert from 'node:assert/strict';
import { loadAdf4j } from '../../npm/adf4j-wasm.mjs';

const here = dirname(fileURLToPath(import.meta.url));
// Source-tree run: use the image under target/. Published installs resolve assets beside the loader.
const targetDir = new URL('../../../target/', import.meta.url);
const adf4j = await loadAdf4j({
  imageUrl: new URL('adf4j-wasm.js', targetDir),
  wasmUrl: new URL('adf4j-wasm.js.wasm', targetDir),
});
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

// Body of a conversion that must succeed; fails the check otherwise.
function markdown(adf, context) {
  const result = adf4j.convert(adf, context === undefined ? undefined : { context });
  assert.equal(result.ok, true, JSON.stringify(result));
  return result.body;
}

function assertFails(result, error) {
  assert.equal(result.ok, false);
  assert.match(result.error, error);
  assert.equal(result.body, "");
}

const mediaDoc = (id) => doc([
  { type: 'paragraph', content: [{ type: 'mediaInline', attrs: { id, collection: 'contentId-1' } }] },
]);

check('analysis plans attachment fetches without rendering', () => {
  const result = adf4j.analyze(mediaDoc('file-1'));
  assert.equal(result.ok, true);
  assert.deepEqual(result.metadata.referencedFileIds, ['file-1']);
});

check('heading renders as ATX', () => {
  const md = markdown(doc([
    { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: 'Title' }] },
  ]));
  assert.equal(md.trim(), '## Title');
});

check('strong + paragraph', () => {
  const md = markdown(doc([
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
  const md = markdown(doc([
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
  const md = markdown(doc([
    { type: 'codeBlock', attrs: { language: 'js' }, content: [{ type: 'text', text: 'a=1' }] },
  ]));
  assert.match(md, /```js[\s\S]*a=1[\s\S]*```/);
});

check('a clean document converts without warnings', () => {
  const r = adf4j.convert(doc([para('plain text')]));

  assert.equal(r.ok, true);
  assert.equal(r.hasWarnings, false);
  assert.deepEqual(r.diagnostics, []);
  assert.equal(r.body.trim(), 'plain text');
});

check('quoted text survives in the body', () => {
  assert.equal(markdown(doc([para('say "hi"')])).trim(), 'say "hi"');
});

check('unknown content produces a warning and a usable placeholder', () => {
  const r = adf4j.convert(doc([{ type: 'bogusBlock' }]));
  assert.equal(r.ok, true);
  assert.equal(r.hasWarnings, true);
  assert.equal(r.diagnostics[0].code, 'UNKNOWN_NODE_PLACEHOLDER');
  assert.equal(r.diagnostics[0].severity, 'WARNING');
});

check('attachment context turns media file nodes into real links', () => {
  const adf = mediaDoc('file-uuid-1');
  const context = {
    attachments: [{
      fileId: 'file-uuid-1',
      title: 'report.xlsx',
      mediaType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      downloadUrl: 'https://example.atlassian.net/wiki/rest/api/content/1/child/attachment/att9/download',
    }],
  };
  const md = markdown(adf, context);
  assert.equal(
    md.trim(),
    '[report.xlsx](https://example.atlassian.net/wiki/rest/api/content/1/child/attachment/att9/download)',
  );

  assert.equal(markdown(adf).trim(), '[file](media:contentId-1/file-uuid-1)');
});

check('an attachment entry needs only a fileId', () => {
  assert.equal(
    markdown(mediaDoc('file-uuid-2'), { attachments: [{ fileId: 'file-uuid-2' }] }).trim(),
    '[file](media:contentId-1/file-uuid-2)',
  );
});

check('an empty attachments array is an authoritative inventory', () => {
  assert.equal(markdown(doc([para('x')]), { attachments: [] }).trim(), 'x');
});

check('context title is a level-1 heading, escaped by the library', () => {
  assert.equal(markdown(doc([para('body')]), { title: 'My *Page*' }), '# My \\*Page\\*\n\nbody');
  assert.equal(markdown(doc([para('body')]), { title: '  ' }).trim(), 'body');
  assert.equal(markdown(doc([para('body')]), { title: null }).trim(), 'body');
});

check('invalid JSON fails without throwing', () => {
  const r = adf4j.convert('{not valid json');
  assertFails(r, /Failed to parse/);
  assert.equal(r.body, '');
  assert.equal(r.hasWarnings, false);
  assert.equal(r.diagnostics[0].code, 'INVALID_JSON');
  assert.equal(r.diagnostics[0].severity, 'ERROR');
});

check('an invalid root fails', () => {
  const r = adf4j.convert(JSON.stringify({ version: 1, type: 'paragraph' }));
  assertFails(r, /doc/);
  assert.equal(r.diagnostics[0].code, 'INVALID_ROOT_NODE');
});

check('blank input reports its cause', () => {
  const r = adf4j.convert('  ');
  assertFails(r, /blank/);
  assert.equal(r.diagnostics[0].code, 'EMPTY_INPUT');
});

check('a non-string input fails without throwing', () => {
  assertFails(adf4j.convert(undefined), /blank/);
  assertFails(adf4j.convert({ type: 'doc' }), /Failed to parse/);
});

check('a failed title-only conversion is still a failure', () => {
  const r = adf4j.convert('{not valid json', { context: { title: 'Page' } });
  assertFails(r, /Failed to parse/);
  assert.equal(r.body, '');
});

for (const [config, message] of [
  [{ context: { attachment: [] } }, /Unknown configuration key: attachment/],
  [{ context: { attachments: [{ title: 'guide.pdf' }] } }, /fileId is required/],
  [{ markdown: { htmlVisualMarks: 'yes' } }, /must be a boolean/],
]) {
  check(`invalid configuration: ${JSON.stringify(config)}`, () => {
    const result = adf4j.convert(doc([para('body')]), config);
    assertFails(result, message);
    assert.equal(result.diagnostics[0].code, 'INVALID_OPTIONS');
  });
}

check('portable rendering policy is honored', () => {
  const result = adf4j.convert(doc([para('(text)')]), {
    markdown: { escapeParentheses: true }, context: { title: 'Report' },
  });
  assert.equal(result.ok, true);
  assert.equal(result.body, '# Report\n\n\\(text\\)');
});

check('unresolved media carries its source location', () => {
  const result = adf4j.convert(mediaDoc('file-1'));
  assert.equal(result.ok, true);
  assert.equal(result.unresolved[0].id, 'file-1');
  assert.equal(result.unresolved[0].path, '/content/0/content/0');
});

await checkSampleFile();
async function checkSampleFile() {
  const adfJson = await readFile(resolve(here, 'sample.adf.json'), 'utf8');
  check('sample.adf.json converts and contains expected fragments', () => {
    const body = markdown(adfJson);
    assert.match(body, /# Release notes/);
    assert.match(body, /\*\*WebAssembly\*\*/);
    assert.match(body, /```bash[\s\S]*node run\.mjs convert/);
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
