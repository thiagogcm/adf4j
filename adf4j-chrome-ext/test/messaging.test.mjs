import assert from 'node:assert/strict';
import test from 'node:test';
import {
  MESSAGE_TYPE,
  createListener,
  validateConversionRequest,
} from '../src/messaging.js';

test('accepts well-formed conversion requests from Confluence Cloud wiki pages', () => {
  const request = validateConversionRequest(
    {
      type: MESSAGE_TYPE,
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
  const request = validateConversionRequest(
    { type: MESSAGE_TYPE, adfJson: '{"type":"doc"}' },
    { tab: { url: 'https://example.com/wiki/spaces/ABC/pages/123/Page' } },
  );

  assert.equal(request.handled, true);
  assert.equal(request.ok, false);
  assert.match(request.error, /Confluence Cloud wiki page/);
});

test('rejects malformed conversion payloads', () => {
  const missingAdf = validateConversionRequest(
    { type: MESSAGE_TYPE, adfJson: '' },
    confluenceSender(),
  );
  assert.equal(missingAdf.ok, false);
  assert.match(missingAdf.error, /Missing ADF JSON/);

  const badContext = validateConversionRequest(
    { type: MESSAGE_TYPE, adfJson: '{"type":"doc"}', context: { unexpected: true } },
    confluenceSender(),
  );
  assert.equal(badContext.ok, false);
  assert.match(badContext.error, /Invalid conversion context/);

  const badAttachment = validateConversionRequest(
    { type: MESSAGE_TYPE, adfJson: '{"type":"doc"}', context: { attachments: [{}] } },
    confluenceSender(),
  );
  assert.equal(badAttachment.ok, false);
  assert.match(badAttachment.error, /Invalid attachment metadata/);
});

test('ignores unsupported message types', () => {
  const request = validateConversionRequest({ type: 'unknown' }, confluenceSender());

  assert.equal(request.handled, false);
});

test('listener responds synchronously to invalid handled requests', () => {
  let response;
  const keepChannelOpen = createListener(async () => ({ ok: true }))(
    { type: MESSAGE_TYPE, adfJson: '{"type":"doc"}' },
    { tab: { url: 'https://example.com/' } },
    (payload) => {
      response = payload;
    },
  );

  assert.equal(keepChannelOpen, false);
  assert.equal(response.ok, false);
  assert.match(response.error, /Confluence Cloud wiki page/);
});

test('listener keeps the channel open and forwards conversion results', async () => {
  const responded = Promise.withResolvers();
  const keepChannelOpen = createListener(async (adfJson) => ({ ok: true, markdown: adfJson }))(
    { type: MESSAGE_TYPE, adfJson: '{"type":"doc"}' },
    confluenceSender(),
    responded.resolve,
  );

  assert.equal(keepChannelOpen, true);
  assert.deepEqual(await responded.promise, { ok: true, markdown: '{"type":"doc"}' });
});

test('listener reports a rejected conversion in-band', async () => {
  const responded = Promise.withResolvers();
  createListener(async () => {
    throw new Error('wasm not ready');
  })(
    { type: MESSAGE_TYPE, adfJson: '{"type":"doc"}' },
    confluenceSender(),
    responded.resolve,
  );

  assert.deepEqual(await responded.promise, { ok: false, error: 'wasm not ready' });
});

function confluenceSender() {
  return {
    tab: {
      url: 'https://example.atlassian.net/wiki/spaces/ABC/pages/123/Page',
    },
  };
}
