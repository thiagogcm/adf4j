(function installCopyAsMarkdownButton() {
  'use strict';

  const helpers = globalThis.Adf4jChromeExt;
  const BUTTON_ID = 'adf4j-copy-markdown-button';
  const MESSAGE_TYPE = 'adf4j.convert';
  const RESET_DELAY_MS = 2200;

  let lastHref = location.href;
  let scheduled = false;
  let activeController;
  let resetTimer;

  patchHistory('pushState');
  patchHistory('replaceState');
  window.addEventListener('popstate', scheduleSync, { passive: true });
  new MutationObserver(scheduleSync).observe(document.documentElement, {
    childList: true,
    subtree: true,
  });

  scheduleSync();

  function patchHistory(methodName) {
    const original = history[methodName];
    history[methodName] = function patchedHistoryMethod(...args) {
      const result = original.apply(this, args);
      scheduleSync();
      return result;
    };
  }

  function scheduleSync() {
    if (scheduled) {
      return;
    }
    scheduled = true;
    window.setTimeout(() => {
      scheduled = false;
      syncButton();
    }, 100);
  }

  function syncButton() {
    const href = location.href;
    const pageId = helpers.extractPageIdFromDocument(document, href);
    if (!helpers.isConfluenceCloudUrl(href) || !pageId) {
      removeButton();
      lastHref = href;
      return;
    }

    const button = ensureButton();
    const pageChanged = button.dataset.pageId !== pageId;
    button.dataset.pageId = pageId;
    if (href !== lastHref || pageChanged) {
      activeController?.abort();
      activeController = undefined;
      setButtonState(button, 'idle');
    }
    lastHref = href;
  }

  function ensureButton() {
    const existing = document.getElementById(BUTTON_ID);
    if (existing) {
      return existing;
    }

    const button = document.createElement('button');
    button.id = BUTTON_ID;
    button.type = 'button';
    button.setAttribute('aria-live', 'polite');
    button.setAttribute('aria-atomic', 'true');
    button.addEventListener('click', () => copyCurrentPage(button));
    setButtonState(button, 'idle');
    document.documentElement.append(button);
    return button;
  }

  function removeButton() {
    document.getElementById(BUTTON_ID)?.remove();
    activeController?.abort();
    activeController = undefined;
  }

  async function copyCurrentPage(button) {
    if (button.dataset.state === 'copying') {
      return;
    }

    activeController?.abort();
    const controller = new AbortController();
    activeController = controller;
    const href = location.href;
    const pageId = helpers.extractPageIdFromDocument(document, href);
    const isCurrent = () => activeController === controller
      && !controller.signal.aborted
      && button.isConnected
      && location.href === href
      && helpers.extractPageIdFromDocument(document, href) === pageId;
    setButtonState(button, 'copying');

    try {
      if (!pageId) {
        throw new Error('Could not identify a Confluence page id.');
      }

      // The attachment inventory is best-effort: without it, links fall back to placeholders.
      const [page, attachments] = await Promise.all([
        fetchJson(helpers.buildPageApiUrl(pageId, href), controller.signal),
        fetchAttachments(pageId, href, controller.signal).catch((error) => {
          if (error?.name === 'AbortError') {
            throw error;
          }
          return undefined;
        }),
      ]);
      if (!isCurrent()) return;
      const conversion = await sendConvertMessage(
        helpers.extractAdfBody(page),
        attachments && { attachments },
      );
      if (!isCurrent()) return;
      if (!conversion?.ok) {
        throw new Error(conversion?.error || 'adf4j conversion failed.');
      }

      const markdown = helpers.formatMarkdownWithTitle(
        helpers.extractPageTitle(document, page),
        conversion.markdown,
      );
      await navigator.clipboard.writeText(markdown);
      if (isCurrent()) setButtonState(button, 'copied');
    } catch (error) {
      if (!isCurrent()) return;
      if (error?.name === 'AbortError') {
        setButtonState(button, 'idle');
        return;
      }
      setButtonState(button, 'failed', error);
    } finally {
      if (activeController === controller) activeController = undefined;
    }
  }

  async function fetchJson(url, signal) {
    const response = await fetch(url, {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      signal,
    });
    if (!response.ok) {
      throw new Error(`Confluence returned ${response.status} ${response.statusText}`);
    }
    return response.json();
  }

  async function fetchAttachments(pageId, href, signal) {
    const attachments = [];
    let url = helpers.buildAttachmentsApiUrl(pageId, href);
    // The cap only guards against a pathological pagination loop.
    for (let fetched = 0; url && fetched < 20; fetched++) {
      const inventoryPage = await fetchJson(url, signal);
      attachments.push(...helpers.extractAttachments(inventoryPage, href));
      url = helpers.nextAttachmentsPageUrl(inventoryPage, href);
    }
    return attachments;
  }

  function sendConvertMessage(adfJson, context) {
    // Promise-form sendMessage rejects where the callback form would set chrome.runtime.lastError.
    return chrome.runtime.sendMessage({ type: MESSAGE_TYPE, adfJson, context });
  }

  function setButtonState(button, state, error) {
    window.clearTimeout(resetTimer);
    button.dataset.state = state;
    button.disabled = state === 'copying';
    button.setAttribute('aria-busy', state === 'copying' ? 'true' : 'false');
    switch (state) {
      case 'copying':
        button.textContent = 'Copying...';
        button.title = 'Fetching and converting the Confluence page';
        button.setAttribute('aria-label', 'Fetching and converting this Confluence page');
        break;
      case 'copied':
        button.textContent = 'Copied';
        button.title = 'Markdown copied to clipboard';
        button.setAttribute('aria-label', 'Markdown copied to clipboard');
        resetButtonSoon(button);
        break;
      case 'failed':
        button.textContent = 'Failed';
        button.title = `Copy failed: ${error?.message || 'unknown error'}`;
        button.setAttribute('aria-label', button.title);
        resetButtonSoon(button);
        break;
      default:
        button.textContent = 'Copy as markdown';
        button.title = 'Copy this Confluence page as Markdown';
        button.setAttribute('aria-label', 'Copy this Confluence page as Markdown');
        break;
    }
  }

  function resetButtonSoon(button) {
    resetTimer = window.setTimeout(() => {
      if (button.isConnected && button.dataset.state !== 'copying') {
        setButtonState(button, 'idle');
      }
    }, RESET_DELAY_MS);
  }
})();
