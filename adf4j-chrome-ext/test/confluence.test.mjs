import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const helpers = await loadHelpers();

test('detects Confluence Cloud wiki URLs only', () => {
  assert.equal(
    helpers.isConfluenceCloudUrl('https://example.atlassian.net/wiki/spaces/ABC/pages/123/Page'),
    true,
  );
  assert.equal(helpers.isConfluenceCloudUrl('https://example.atlassian.net/jira/software'), false);
  assert.equal(helpers.isConfluenceCloudUrl('https://confluence.example.com/wiki/pages/123'), false);
});

test('extracts page ids from modern and legacy URLs', () => {
  assert.equal(
    helpers.extractPageIdFromUrl('https://example.atlassian.net/wiki/spaces/ABC/pages/123/Page'),
    '123',
  );
  assert.equal(
    helpers.extractPageIdFromUrl(
      'https://example.atlassian.net/wiki/pages/viewpage.action?pageId=456',
    ),
    '456',
  );
  assert.equal(
    helpers.extractPageIdFromUrl('https://example.atlassian.net/wiki/spaces/ABC/overview'),
    '',
  );
});

test('extracts page ids from canonical links and metadata', () => {
  assert.equal(
    helpers.extractPageIdFromDocument(
      fakeDocument({
        'link[rel="canonical"]': element({ href: 'https://example.atlassian.net/wiki/spaces/X/pages/789/Title' }),
      }),
      'https://example.atlassian.net/wiki/spaces/X/overview',
    ),
    '789',
  );

  assert.equal(
    helpers.extractPageIdFromDocument(
      fakeDocument({
        'meta[name="ajs-page-id"]': element({ content: '987' }),
      }),
      'https://example.atlassian.net/wiki/spaces/X/overview',
    ),
    '987',
  );
});

test('detects Confluence page context only when a page id is available', () => {
  assert.equal(
    helpers.isConfluencePageContext(
      fakeDocument({}),
      'https://example.atlassian.net/wiki/spaces/ABC/pages/123/Page',
    ),
    true,
  );
  assert.equal(
    helpers.isConfluencePageContext(fakeDocument({}), 'https://example.atlassian.net/wiki/spaces/ABC/overview'),
    false,
  );
});

test('builds the Confluence REST v2 ADF URL', () => {
  assert.equal(
    helpers.buildPageApiUrl('123', 'https://example.atlassian.net/wiki/spaces/ABC/pages/123/Page'),
    'https://example.atlassian.net/wiki/api/v2/pages/123?body-format=atlas_doc_format',
  );
});

test('builds the Confluence REST v2 attachments URL', () => {
  assert.equal(
    helpers.buildAttachmentsApiUrl('123', 'https://example.atlassian.net/wiki/spaces/ABC/pages/123/Page'),
    'https://example.atlassian.net/wiki/api/v2/pages/123/attachments?limit=250',
  );
});

test('extracts attachments with absolute download URLs', () => {
  const baseUrl = 'https://example.atlassian.net/wiki/spaces/ABC/pages/123/Page';
  // vm-context objects carry a foreign prototype; a JSON round-trip normalizes them for deepEqual.
  const plain = (value) => JSON.parse(JSON.stringify(value));
  assert.deepEqual(
    plain(helpers.extractAttachments(
      {
        results: [
          {
            fileId: 'uuid-1',
            title: 'report.xlsx',
            mediaType: 'application/vnd.ms-excel',
            downloadLink: '/rest/api/content/123/child/attachment/att9/download',
          },
          {
            fileId: 'uuid-2',
            title: 'prefixed.pdf',
            mediaType: 'application/pdf',
            downloadLink: '/wiki/download/attachments/123/prefixed.pdf',
          },
          {
            fileId: 'uuid-3',
            title: 'absolute.png',
            mediaType: 'image/png',
            downloadLink: 'https://cdn.example.com/absolute.png',
          },
          { title: 'no file id, skipped', downloadLink: '/rest/whatever' },
        ],
      },
      baseUrl,
    )),
    [
      {
        fileId: 'uuid-1',
        title: 'report.xlsx',
        mediaType: 'application/vnd.ms-excel',
        downloadUrl:
          'https://example.atlassian.net/wiki/rest/api/content/123/child/attachment/att9/download',
      },
      {
        fileId: 'uuid-2',
        title: 'prefixed.pdf',
        mediaType: 'application/pdf',
        downloadUrl: 'https://example.atlassian.net/wiki/download/attachments/123/prefixed.pdf',
      },
      {
        fileId: 'uuid-3',
        title: 'absolute.png',
        mediaType: 'image/png',
        downloadUrl: 'https://cdn.example.com/absolute.png',
      },
    ],
  );
  assert.deepEqual(plain(helpers.extractAttachments({}, baseUrl)), []);
});

test('resolves the next attachments page URL against the site origin', () => {
  const baseUrl = 'https://example.atlassian.net/wiki/spaces/ABC/pages/123/Page';
  assert.equal(
    helpers.nextAttachmentsPageUrl(
      { _links: { next: '/wiki/api/v2/pages/123/attachments?limit=250&cursor=abc' } },
      baseUrl,
    ),
    'https://example.atlassian.net/wiki/api/v2/pages/123/attachments?limit=250&cursor=abc',
  );
  assert.equal(helpers.nextAttachmentsPageUrl({ _links: {} }, baseUrl), '');
  assert.equal(helpers.nextAttachmentsPageUrl({}, baseUrl), '');
});

test('extracts ADF JSON body from Confluence responses', () => {
  assert.equal(
    helpers.extractAdfBody({ body: { atlas_doc_format: { value: '{"type":"doc"}' } } }),
    '{"type":"doc"}',
  );
  assert.equal(
    helpers.extractAdfBody({ body: { atlas_doc_format: { value: { type: 'doc' } } } }),
    '{"type":"doc"}',
  );
  assert.throws(() => helpers.extractAdfBody({ body: {} }), /atlas_doc_format/);
});

test('formats copied markdown with the page title', () => {
  assert.equal(helpers.formatMarkdownWithTitle('My Page', 'body\n'), '# My Page\n\nbody\n');
  assert.equal(helpers.formatMarkdownWithTitle('  My   Page  ', 'body'), '# My Page\n\nbody');
  assert.equal(helpers.formatMarkdownWithTitle('', 'body'), 'body');
});

test('extracts title from response, metadata, or document title', () => {
  assert.equal(helpers.extractPageTitle(fakeDocument({}, 'Ignored'), { title: 'API Title' }), 'API Title');
  assert.equal(
    helpers.extractPageTitle(
      fakeDocument({ 'meta[name="ajs-page-title"]': element({ content: 'Meta Title' }) }),
      {},
    ),
    'Meta Title',
  );
  assert.equal(helpers.extractPageTitle(fakeDocument({}, 'Doc Title - Confluence'), {}), 'Doc Title');
});

async function loadHelpers() {
  const source = await readFile(new URL('../src/confluence.js', import.meta.url), 'utf8');
  const context = vm.createContext({ URL });
  vm.runInContext(source, context, { filename: 'confluence.js' });
  return context.Adf4jChromeExt;
}

function fakeDocument(selectors, title = '') {
  return {
    title,
    querySelector(selector) {
      return selectors[selector] ?? null;
    },
  };
}

function element(attributes) {
  return {
    ...attributes,
    getAttribute(name) {
      return attributes[name] ?? null;
    },
  };
}
