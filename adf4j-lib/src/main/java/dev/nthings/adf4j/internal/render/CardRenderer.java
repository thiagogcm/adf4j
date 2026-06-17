package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.CardAttrs;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/// Renders smart-link nodes (block/inline/embed cards) to Markdown links.
final class CardRenderer {

  String renderBlockCard(CardAttrs attrs, RenderContext context) {
    var link = renderCardLink(attrs, context);
    if (link != null) {
      return link;
    }

    var identifier =
        Stream.of(attrs.datasourceId(), attrs.localId())
            .filter(s -> s != null && !s.isBlank())
            .findFirst()
            .orElse(null);
    if (identifier == null) {
      return MarkdownText.labelToken("Card", context.options().escapeParentheses());
    }
    return MarkdownText.labelToken("Card: " + identifier, context.options().escapeParentheses());
  }

  String renderInlineCard(CardAttrs attrs, RenderContext context) {
    var link = renderCardLink(attrs, context);
    return link != null
        ? link
        : MarkdownText.labelToken("Inline card", context.options().escapeParentheses());
  }

  String renderEmbedCard(CardAttrs attrs, RenderContext context) {
    var link = renderCardLink(attrs, context);
    return link != null
        ? link
        : MarkdownText.labelToken("Embed card", context.options().escapeParentheses());
  }

  /// Shared url/title rendering for all three card kinds, or `null` when the card has neither:
  /// url+title -> `[title](url)`; url only -> `<url>` (or `[url](url)` if not clean); title only ->
  /// the escaped title as plain text. A `PageLinkResolver` rewrites an internal page card's
  /// destination; a url-only card whose destination is rewritten keeps the original url as its
  /// visible label.
  private @Nullable String renderCardLink(CardAttrs attrs, RenderContext context) {
    var url = attrs.url();
    var title = attrs.title();
    var hasTitle = title != null && !title.isBlank();

    if (url != null && !url.isBlank()) {
      var resolvedUrl = TextMarkRenderer.resolvePageHref(url, attrs.attrs(), context);
      if (hasTitle) {
        return MarkdownText.link(title, resolvedUrl, context.options().escapeParentheses());
      }
      // url-only: an autolink needs an untouched url (not rewritten, not escaped); otherwise fall
      // back to a labelled link that keeps the original url visible.
      var rewritten = !resolvedUrl.equals(url);
      var destination = MarkdownText.escapeUrlDestination(resolvedUrl);
      return !rewritten && url.equals(destination) && isAbsoluteUri(url)
          ? "<%s>".formatted(url)
          : MarkdownText.link(url, resolvedUrl, context.options().escapeParentheses());
    }

    if (hasTitle) {
      return MarkdownText.escapeInlineText(title, false, context.options().escapeParentheses());
    }

    return null;
  }

  /// A CommonMark absolute-URI scheme: a letter, then letters/digits/+/-/., then ':' (min 2 chars).
  private static boolean isAbsoluteUri(String url) {
    var colon = url.indexOf(':');
    if (colon < 2 || !Character.isLetter(url.charAt(0))) {
      return false;
    }
    for (var i = 1; i < colon; i++) {
      var c = url.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '+' && c != '.' && c != '-') {
        return false;
      }
    }
    return true;
  }
}
