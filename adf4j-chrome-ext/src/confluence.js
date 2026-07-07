(function installConfluenceHelpers(global) {
  'use strict';

  const PAGE_PATH_PATTERN = /\/wiki\/(?:spaces\/[^/?#]+\/pages|pages)\/(\d+)(?:[/?#]|$)/;

  function isConfluenceCloudUrl(url) {
    const parsed = parseUrl(url);
    return Boolean(
      parsed
        && parsed.protocol === 'https:'
        && parsed.hostname.endsWith('.atlassian.net')
        && parsed.pathname.startsWith('/wiki/'),
    );
  }

  function extractPageIdFromUrl(url) {
    const parsed = parseUrl(url);
    if (!parsed) {
      return '';
    }

    const queryPageId = normalizePageId(parsed.searchParams.get('pageId'));
    if (queryPageId && parsed.pathname.startsWith('/wiki/')) {
      return queryPageId;
    }

    const pathMatch = parsed.pathname.match(PAGE_PATH_PATTERN);
    return normalizePageId(pathMatch?.[1]);
  }

  function extractPageIdFromDocument(documentRef, url) {
    const fromUrl = extractPageIdFromUrl(url);
    if (fromUrl) {
      return fromUrl;
    }

    const canonicalUrl = readDomValue(documentRef, ['link[rel="canonical"]'], 'href');
    const fromCanonical = extractPageIdFromUrl(canonicalUrl);
    if (fromCanonical) {
      return fromCanonical;
    }

    const metadataUrl = readDomValue(
      documentRef,
      ['meta[property="og:url"]', 'meta[name="ajs-page-url"]'],
      'content',
    );
    const fromMetadataUrl = extractPageIdFromUrl(metadataUrl);
    if (fromMetadataUrl) {
      return fromMetadataUrl;
    }

    return normalizePageId(
      readDomValue(
        documentRef,
        [
          'meta[name="ajs-page-id"]',
          'meta[name="ajs-content-id"]',
          'meta[name="ajs-draft-id"]',
        ],
        'content',
      ),
    );
  }

  function isConfluencePageContext(documentRef, url) {
    return isConfluenceCloudUrl(url) && Boolean(extractPageIdFromDocument(documentRef, url));
  }

  function buildPageApiUrl(pageId, baseUrl) {
    const parsed = parseUrl(baseUrl);
    if (!parsed) {
      throw new Error(`Invalid Confluence URL: ${baseUrl}`);
    }
    const apiUrl = new URL(`/wiki/api/v2/pages/${encodeURIComponent(pageId)}`, parsed.origin);
    apiUrl.searchParams.set('body-format', 'atlas_doc_format');
    return apiUrl.href;
  }

  function extractAdfBody(pageResponse) {
    const adf = pageResponse?.body?.atlas_doc_format;
    const value = typeof adf === 'string' ? adf : adf?.value;
    if (typeof value === 'string' && value.trim()) {
      return value;
    }
    if (value && typeof value === 'object') {
      return JSON.stringify(value);
    }
    throw new Error('Confluence response did not include body.atlas_doc_format.value');
  }

  function extractPageTitle(documentRef, pageResponse) {
    const responseTitle = pageResponse?.title;
    if (typeof responseTitle === 'string' && responseTitle.trim()) {
      return collapseWhitespace(responseTitle);
    }

    const metadataTitle = readDomValue(
      documentRef,
      ['meta[name="ajs-page-title"]', 'meta[property="og:title"]'],
      'content',
    );
    if (metadataTitle) {
      return collapseWhitespace(metadataTitle);
    }

    const documentTitle = typeof documentRef?.title === 'string' ? documentRef.title : '';
    return collapseWhitespace(documentTitle.replace(/\s+-\s+Confluence\s*$/i, ''));
  }

  function formatMarkdownWithTitle(title, markdown) {
    const normalizedTitle = collapseWhitespace(title);
    const body = String(markdown ?? '').trimStart();
    if (!normalizedTitle) {
      return body;
    }
    return body ? `# ${normalizedTitle}\n\n${body}` : `# ${normalizedTitle}\n`;
  }

  function readDomValue(documentRef, selectors, attribute) {
    for (const selector of selectors) {
      const element = documentRef?.querySelector?.(selector);
      const rawValue =
        typeof element?.getAttribute === 'function'
          ? element.getAttribute(attribute)
          : element?.[attribute];
      if (typeof rawValue === 'string' && rawValue.trim()) {
        return rawValue.trim();
      }
    }
    return '';
  }

  function normalizePageId(value) {
    const text = typeof value === 'string' ? value.trim() : '';
    return /^\d+$/.test(text) ? text : '';
  }

  function collapseWhitespace(value) {
    return String(value ?? '').replace(/\s+/g, ' ').trim();
  }

  function parseUrl(url) {
    try {
      return new URL(String(url));
    } catch {
      return undefined;
    }
  }

  global.Adf4jChromeExt = Object.freeze({
    buildPageApiUrl,
    extractAdfBody,
    extractPageIdFromDocument,
    extractPageIdFromUrl,
    extractPageTitle,
    formatMarkdownWithTitle,
    isConfluenceCloudUrl,
    isConfluencePageContext,
  });
})(globalThis);
