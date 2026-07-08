import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const background = await loadBackground();

test('accepts well-formed conversion requests from Confluence Cloud wiki pages', () => {
  const request = background.validateConversionRequest(
    {
      type: background.MESSAGE_TYPE,
      adfJson: '{"type":"doc","content":[]}',
      context: {
        attachments: [
          {
            fileId: 'file-1',
            title: 'Report.pdf',
            mediaType: 'application/pdf',
            downloadUrl: 'https://example.atlassian.net/wiki/download/attachments/123/Report.pdf',
          },
        ],
      },
    },
    confluenceSender(),
  );

  assert.equal(request.handled, true);
  assert.equal(request.ok, true);
  assert.equal(request.context.attachments[0].fileId, 'file-1');
});

test('rejects conversion requests from non-Confluence sender tab URLs', () => {
  const request = background.validateConversionRequest(
    { type: background.MESSAGE_TYPE, adfJson: '{"type":"doc"}' },
    { tab: { url: 'https://example.com/wiki/spaces/ABC/pages/123/Page' } },
  );

  assert.equal(request.handled, true);
  assert.equal(request.ok, false);
  assert.match(request.error, /Confluence Cloud wiki page/);
});

test('rejects malformed conversion payloads', () => {
  const missingAdf = background.validateConversionRequest(
    { type: background.MESSAGE_TYPE, adfJson: '' },
    confluenceSender(),
  );
  assert.equal(missingAdf.ok, false);
  assert.match(missingAdf.error, /Missing ADF JSON/);

  const badContext = background.validateConversionRequest(
    { type: background.MESSAGE_TYPE, adfJson: '{"type":"doc"}', context: { unexpected: true } },
    confluenceSender(),
  );
  assert.equal(badContext.ok, false);
  assert.match(badContext.error, /Invalid conversion context/);

  const badAttachment = background.validateConversionRequest(
    { type: background.MESSAGE_TYPE, adfJson: '{"type":"doc"}', context: { attachments: [{}] } },
    confluenceSender(),
  );
  assert.equal(badAttachment.ok, false);
  assert.match(badAttachment.error, /Invalid attachment metadata/);
});

test('ignores unsupported message types', () => {
  const request = background.validateConversionRequest({ type: 'unknown' }, confluenceSender());

  assert.equal(request.handled, false);
});

test('listener responds synchronously to invalid handled requests', () => {
  let response;
  const keepChannelOpen = background.listener(
    { type: background.MESSAGE_TYPE, adfJson: '{"type":"doc"}' },
    { tab: { url: 'https://example.com/' } },
    (payload) => {
      response = payload;
    },
  );

  assert.equal(keepChannelOpen, false);
  assert.equal(response.ok, false);
  assert.match(response.error, /Confluence Cloud wiki page/);
});

async function loadBackground() {
  const source = await readFile(new URL('../src/background.js', import.meta.url), 'utf8');
  let listener;
  const context = vm.createContext({
    URL,
    console,
    globalThis: {},
    importScripts() {},
    setTimeout() {
      return 1;
    },
    clearTimeout() {},
    chrome: {
      runtime: {
        getURL(path) {
          return `chrome-extension://adf4j/${path}`;
        },
        onMessage: {
          addListener(callback) {
            listener = callback;
          },
        },
      },
    },
  });
  context.globalThis = context;
  vm.runInContext(source, context, { filename: 'background.js' });
  return { ...context.Adf4jChromeExtBackground, listener };
}

function confluenceSender() {
  return {
    tab: {
      url: 'https://example.atlassian.net/wiki/spaces/ABC/pages/123/Page',
    },
  };
}
