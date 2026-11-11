package dev.nthings.adf4j.internal.render;

import org.jspecify.annotations.Nullable;

/// {@link MarkdownText} operations bound to one render's `escapeParentheses`.
record MarkdownWriter(boolean escapeParentheses) {

  /// Inline-escaped literal text; `atLineStart` also neutralizes a leading block marker.
  /// Null is empty.
  String text(@Nullable String value, boolean atLineStart) {
    return MarkdownText.escapeInlineText(value, atLineStart, escapeParentheses);
  }

  /// A literal `[inner]` label token, inline-escaped so it can't parse as a link.
  String label(String inner) {
    return MarkdownText.labelToken(inner, escapeParentheses);
  }

  /// A Markdown inline link `[label](url)`; a null label is empty.
  String link(@Nullable String label, String url) {
    return MarkdownText.link(label, url, escapeParentheses);
  }

  /// Image alt text, with label-ending characters escaped.
  String alt(String alt) {
    return MarkdownText.escapeAltText(alt, escapeParentheses);
  }
}
