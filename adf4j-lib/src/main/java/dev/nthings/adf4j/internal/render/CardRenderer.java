package dev.nthings.adf4j.internal.render;

import java.util.stream.Stream;

import dev.nthings.adf4j.ast.CardAttrs;

/** Renders smart-link nodes (block/inline/embed cards) to Markdown links. */
final class CardRenderer {

  String renderBlockCard(CardAttrs attrs) {
    var link = renderCardLink(attrs);
    if (link != null) {
      return link;
    }

    var identifier = Stream.of(attrs.datasourceId(), attrs.localId())
        .filter(s -> s != null && !s.isBlank())
        .findFirst()
        .orElse(null);
    if (identifier == null) {
      return MarkdownText.labelToken("Card");
    }
    return MarkdownText.labelToken("Card: " + identifier);
  }

  String renderInlineCard(CardAttrs attrs) {
    var link = renderCardLink(attrs);
    return link != null ? link : MarkdownText.labelToken("Inline card");
  }

  String renderEmbedCard(CardAttrs attrs) {
    var link = renderCardLink(attrs);
    return link != null ? link : MarkdownText.labelToken("Embed card");
  }

  /**
   * Shared url/title rendering for all three card kinds, or {@code null} when the card has neither:
   * url+title -&gt; {@code [title](url)}; url only -&gt; {@code <url>} (or {@code [url](url)} if not
   * clean); title only -&gt; the escaped title as plain text.
   */
  private String renderCardLink(CardAttrs attrs) {
    var url = attrs.url();
    var hasUrl = url != null && !url.isBlank();

    var title = attrs.title();
    var hasTitle = title != null && !title.isBlank();

    if (hasUrl) {
      if (hasTitle) {
        return "[%s](%s)".formatted(
            MarkdownText.escapeInlineText(title, false), MarkdownText.escapeUrlDestination(url));
      }
      var destination = MarkdownText.escapeUrlDestination(url);
      return destination.equals(url) && isAbsoluteUri(url)
          ? "<%s>".formatted(url)
          : "[%s](%s)".formatted(url, destination);
    }

    if (hasTitle) {
      return MarkdownText.escapeInlineText(title, false);
    }

    return null;
  }

  // A CommonMark absolute-URI scheme: a letter, then letters/digits/+/-/., then ':' (min 2 chars).
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
