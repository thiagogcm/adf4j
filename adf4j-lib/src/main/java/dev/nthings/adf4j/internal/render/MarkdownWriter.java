package dev.nthings.adf4j.internal.render;

import org.jspecify.annotations.Nullable;

/// The {@link MarkdownText} operations that depend on `escapeParentheses`, bound to one render's
/// value of that option.
record MarkdownWriter(boolean escapeParentheses) {

  /// Literal text, inline-escaped. `atLineStart` also neutralizes a leading block marker. Null is
  /// treated as empty.
  String text(@Nullable String value, boolean atLineStart) {
    return MarkdownText.escapeInlineText(value, atLineStart, escapeParentheses);
  }

  /// A literal `[inner]` label token, fully inline-escaped so it can't parse as a link.
  String label(String inner) {
    return MarkdownText.labelToken(inner, escapeParentheses);
  }

  /// A Markdown inline link `[label](url)` from raw operands. A null label is treated as empty.
  String link(@Nullable String label, String url) {
    return MarkdownText.link(label, url, escapeParentheses);
  }

  /// Image alt text, with the characters that would end the label escaped.
  String alt(String alt) {
    return MarkdownText.escapeAltText(alt, escapeParentheses);
  }
}
