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
      return "[Card]";
    }
    return "[Card: %s]".formatted(identifier);
  }

  String renderInlineCard(CardAttrs attrs) {
    var link = renderCardLink(attrs);
    return link != null ? link : "[Inline card]";
  }

  String renderEmbedCard(CardAttrs attrs) {
    var link = renderCardLink(attrs);
    return link != null ? link : "[Embed card]";
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
            MarkdownText.escapeLinkText(title), MarkdownText.escapeUrlDestination(url));
      }
      var destination = MarkdownText.escapeUrlDestination(url);
      return destination.equals(url) ? "<%s>".formatted(url) : "[%s](%s)".formatted(url, destination);
    }

    if (hasTitle) {
      return MarkdownText.escapeLinkText(title);
    }

    return null;
  }
}
