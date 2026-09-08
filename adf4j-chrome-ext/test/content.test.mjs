import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';

const helpersSource = readFileSync(new URL('../src/confluence.js', import.meta.url), 'utf8');
const contentSource = readFileSync(new URL('../src/content.js', import.meta.url), 'utf8');
const tick = () => new Promise((resolve) => setImmediate(resolve));

test('a late conversion cannot overwrite the newly copied page', async () => {
  const page = browser();
  const first = page.click();
  await tick();
  page.navigate('456');
  const second = page.click();
  await tick();
  page.conversions[1].resolve({ ok: true, markdown: 'Body B' });
  await second;
  page.conversions[0].resolve({ ok: true, markdown: 'Body A' });
  await first;
  assert.deepEqual(page.clipboard, ['# Page 456\n\nBody B']);
  assert.equal(page.button.dataset.state, 'copied');
});

test('stale errors and cleanup cannot change or detach the active operation', async () => {
  const page = browser();
  const first = page.click();
  await tick();
  page.navigate('456');
  const second = page.click();
  await tick();
  page.conversions[0].reject(new Error('old conversion failed'));
  await first;
  assert.equal(page.button.dataset.state, 'copying');
  page.navigate('789');
  assert.equal(page.signals.get('456').aborted, true);
  page.conversions[1].resolve({ ok: true, markdown: 'Body B' });
  await second;
  assert.deepEqual(page.clipboard, []);
  assert.equal(page.button.dataset.state, 'idle');
});

test('navigation invalidates a result before the debounced DOM update runs', async () => {
  const page = browser();
  const copy = page.click();
  await tick();
  page.navigate('456', false);
  page.conversions[0].resolve({ ok: true, markdown: 'Old page' });
  await copy;
  assert.deepEqual(page.clipboard, []);
});

test('a clipboard completion from an old operation cannot update the new button state', async () => {
  const written = Promise.withResolvers();
  const page = browser(() => written.promise);
  const first = page.click();
  await tick();
  page.conversions[0].resolve({ ok: true, markdown: 'Body A' });
  await tick();
  page.navigate('456');
  const second = page.click();
  await tick();
  written.resolve();
  await first;
  assert.equal(page.button.dataset.state, 'copying');
  page.conversions[1].resolve({ ok: true, markdown: 'Body B' });
  await second;
  assert.equal(page.button.dataset.state, 'copied');
});

test('an earlier reset timer cannot shorten the latest copied state', async () => {
  const page = browser();
  const first = page.click();
  await tick();
  page.conversions[0].resolve({ ok: true, markdown: 'First' });
  await first;
  const oldReset = page.timerIds(2200)[0];
  const second = page.click();
  await tick();
  page.conversions[1].resolve({ ok: true, markdown: 'Second' });
  await second;
  page.fire(oldReset);
  assert.equal(page.button.dataset.state, 'copied');
  page.fire(page.timerIds(2200)[0]);
  assert.equal(page.button.dataset.state, 'idle');
});

test('current conversion errors remain visible and allow a successful retry', async () => {
  const page = browser();
  const first = page.click();
  await tick();
  page.conversions[0].reject(new Error('conversion failed'));
  await first;
  assert.equal(page.button.dataset.state, 'failed');
  assert.match(page.button.title, /conversion failed/);
  const retry = page.click();
  await tick();
  page.conversions[1].resolve({ ok: true, markdown: 'Recovered' });
  await retry;
  assert.deepEqual(page.clipboard, ['# Page 123\n\nRecovered']);
  assert.equal(page.button.dataset.state, 'copied');
});

function browser(writeClipboard = async () => {}) {
  const timers = new Map();
  let timerId = 0;
  let button;
  const conversions = [];
  const clipboard = [];
  const signals = new Map();
  const location = { href: 'https://example.atlassian.net/wiki/pages/123' };
  const document = {
    title: 'Confluence',
    querySelector: () => null,
    documentElement: {
      append(value) {
        button = value;
        button.isConnected = true;
      },
    },
    getElementById: () => button,
    createElement: () => ({
      dataset: {},
      setAttribute() {},
      addEventListener(type, callback) {
        this[type] = callback;
      },
      remove() {
        this.isConnected = false;
        button = undefined;
      },
    }),
  };
  const sandbox = {
    URL, AbortController, location, document,
    window: {
      setTimeout(callback, ms) {
        const id = ++timerId;
        timers.set(id, { callback, ms });
        return id;
      },
      clearTimeout(id) {
        timers.delete(id);
      },
      addEventListener() {},
    },
    history: { pushState(_state, _title, href) { location.href = href; }, replaceState() {} },
    MutationObserver: class { observe() {} },
    navigator: {
      clipboard: {
        async writeText(value) {
          clipboard.push(value);
          await writeClipboard(value);
        },
      },
    },
    chrome: {
      runtime: {
        sendMessage() {
          const conversion = Promise.withResolvers();
          conversions.push(conversion);
          return conversion.promise;
        },
      },
    },
    async fetch(url, { signal }) {
      const id = new URL(url).pathname.match(/pages\/(\d+)/)[1];
      signals.set(id, signal);
      return {
        ok: true,
        json: async () => url.includes('/attachments') ? { results: [] } : {
          title: `Page ${id}`, body: { atlas_doc_format: { value: '{}' } },
        },
      };
    },
  };
  vm.createContext(sandbox);
  vm.runInContext(helpersSource, sandbox);
  vm.runInContext(contentSource, sandbox);
  const fire = (id) => {
    const timer = timers.get(id);
    timers.delete(id);
    timer?.callback();
  };
  const timerIds = (ms) => [...timers].filter(([, timer]) => timer.ms === ms).map(([id]) => id);
  const sync = () => fire(timerIds(100)[0]);
  sync();
  return {
    conversions, clipboard, signals, timerIds, fire,
    get button() { return button; },
    click: () => button.click(),
    navigate(id, synchronize = true) {
      sandbox.history.pushState(null, '', `https://example.atlassian.net/wiki/pages/${id}`);
      if (synchronize) sync();
    },
  };
}
