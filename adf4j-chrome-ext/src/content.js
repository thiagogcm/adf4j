(function installCopyAsMarkdownButton() {
  'use strict';

  const helpers = globalThis.Adf4jChromeExt;
  const BUTTON_ID = 'adf4j-copy-markdown-button';
  const MESSAGE_TYPE = 'adf4j.convert';
  const RESET_DELAY_MS = 2200;

  let lastHref = location.href;
  let scheduled = false;
  let activeController;

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
    button.dataset.pageId = pageId;
    if (href !== lastHref) {
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
    button.textContent = 'Copy as markdown';
    button.addEventListener('click', () => copyCurrentPage(button));
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
    activeController = new AbortController();
    setButtonState(button, 'copying');

    try {
      const pageId = button.dataset.pageId || helpers.extractPageIdFromDocument(document, location.href);
      if (!pageId) {
        throw new Error('Could not identify a Confluence page id.');
      }

      const response = await fetch(helpers.buildPageApiUrl(pageId, location.href), {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        signal: activeController.signal,
      });
      if (!response.ok) {
        throw new Error(`Confluence returned ${response.status} ${response.statusText}`);
      }

      const page = await response.json();
      const conversion = await sendConvertMessage(helpers.extractAdfBody(page));
      if (!conversion?.ok) {
        throw new Error(conversion?.error || 'adf4j conversion failed.');
      }

      const markdown = helpers.formatMarkdownWithTitle(
        helpers.extractPageTitle(document, page),
        conversion.markdown,
      );
      await navigator.clipboard.writeText(markdown);
      setButtonState(button, 'copied');
    } catch (error) {
      if (error?.name === 'AbortError') {
        setButtonState(button, 'idle');
        return;
      }
      setButtonState(button, 'failed', error);
    } finally {
      if (activeController?.signal.aborted || activeController) {
        activeController = undefined;
      }
    }
  }

  function sendConvertMessage(adfJson) {
    return new Promise((resolve, reject) => {
      chrome.runtime.sendMessage({ type: MESSAGE_TYPE, adfJson }, (response) => {
        const runtimeError = chrome.runtime.lastError;
        if (runtimeError) {
          reject(new Error(runtimeError.message));
          return;
        }
        resolve(response);
      });
    });
  }

  function setButtonState(button, state, error) {
    button.dataset.state = state;
    button.disabled = state === 'copying';
    switch (state) {
      case 'copying':
        button.textContent = 'Copying...';
        button.title = 'Fetching and converting the Confluence page';
        break;
      case 'copied':
        button.textContent = 'Copied';
        button.title = 'Markdown copied to clipboard';
        resetButtonSoon(button);
        break;
      case 'failed':
        button.textContent = 'Failed';
        button.title = error?.message || 'Copy failed';
        resetButtonSoon(button);
        break;
      default:
        button.textContent = 'Copy as markdown';
        button.title = 'Copy this Confluence page as Markdown';
        break;
    }
  }

  function resetButtonSoon(button) {
    window.setTimeout(() => {
      if (button.isConnected && button.dataset.state !== 'copying') {
        setButtonState(button, 'idle');
      }
    }, RESET_DELAY_MS);
  }
})();
