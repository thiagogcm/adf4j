// Conversion-request validation and the onMessage listener, kept chrome-free so tests can
// import it directly.
import './confluence.js';

const { isConfluenceCloudUrl } = globalThis.Adf4jChromeExt;

export const MESSAGE_TYPE = 'adf4j.convert';
const MAX_ATTACHMENTS = 5_000;

// Returns the listener for chrome.runtime.onMessage; `convert` is async (adfJson, context?).
export function createListener(convert) {
  return (message, sender, sendResponse) => {
    const request = validateConversionRequest(message, sender);
    if (!request.handled) {
      return false;
    }

    if (!request.ok) {
      sendResponse({ ok: false, error: request.error });
      return false;
    }

    convert(request.adfJson, request.context).then(sendResponse, (error) => {
      sendResponse({ ok: false, error: error?.message || String(error) });
    });
    return true;
  };
}

export function validateConversionRequest(message, sender) {
  if (!message || message.type !== MESSAGE_TYPE) {
    return { handled: false };
  }

  if (!isConfluenceCloudUrl(sender?.tab?.url)) {
    return {
      handled: true,
      ok: false,
      error: 'Conversion requests must originate from a Confluence Cloud wiki page.',
    };
  }

  if (typeof message.adfJson !== 'string' || !message.adfJson.trim()) {
    return { handled: true, ok: false, error: 'Missing ADF JSON payload.' };
  }

  const context = sanitizeConversionContext(message.context);
  if (!context.ok) {
    return { handled: true, ok: false, error: context.error };
  }

  return {
    handled: true,
    ok: true,
    adfJson: message.adfJson,
    context: context.value,
  };
}

export function sanitizeConversionContext(context) {
  if (context === undefined || context === null) {
    return { ok: true, value: undefined };
  }
  if (!isPlainObject(context)) {
    return { ok: false, error: 'Invalid conversion context payload.' };
  }

  const keys = Object.keys(context);
  if (keys.some((key) => key !== 'attachments')) {
    return { ok: false, error: 'Invalid conversion context payload.' };
  }

  if (context.attachments === undefined) {
    return { ok: true, value: undefined };
  }
  if (!Array.isArray(context.attachments) || context.attachments.length > MAX_ATTACHMENTS) {
    return { ok: false, error: 'Invalid attachment metadata payload.' };
  }

  const attachments = [];
  for (const item of context.attachments) {
    if (!isPlainObject(item) || typeof item.fileId !== 'string' || !item.fileId.trim()) {
      return { ok: false, error: 'Invalid attachment metadata payload.' };
    }
    attachments.push({
      fileId: item.fileId.trim(),
      title: optionalString(item.title),
      mediaType: optionalString(item.mediaType),
      downloadUrl: optionalString(item.downloadUrl),
    });
  }

  return { ok: true, value: { attachments } };
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function optionalString(value) {
  return typeof value === 'string' ? value : '';
}
