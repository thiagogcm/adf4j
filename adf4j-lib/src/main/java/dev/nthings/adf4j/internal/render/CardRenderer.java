package dev.nthings.adf4j.internal.render;

import java.util.stream.Stream;

import dev.nthings.adf4j.ast.CardAttrs;

/** Renders smart-link nodes (block/inline/embed cards) to Markdown links. */
final class CardRenderer {

  String renderBlockCard(CardAttrs attrs) {
    var renderedUrl = renderCardUrl(attrs);
    if (renderedUrl != null) {
      return renderedUrl;
    }

    var identifier = Stream.of(attrs.datasourceId(), attrs.localId())
        .filter(s -> s != null && !s.isBlank())
        .findFirst()
        .orElse(null);
    if (identifier == null || identifier.isBlank()) {
      return "[Card]";
    }
    return "[Card: %s]".formatted(identifier);
  }

  String renderInlineCard(CardAttrs attrs) {
    var url = renderCardUrl(attrs);
    return url != null ? url : "[Inline card]";
  }

  String renderEmbedCard(CardAttrs attrs) {
    var url = attrs.url();
    if (url == null || url.isBlank()) {
      return "[Embed card]";
    }

    var explicitTitle = attrs.title();
    if (explicitTitle != null && !explicitTitle.isBlank()) {
      return "[%s](%s)".formatted(MarkdownText.escapeLinkText(explicitTitle), url);
    }
    return "<%s>".formatted(url);
  }

  private String renderCardUrl(CardAttrs attrs) {
    var url = attrs.url();
    if (url == null || url.isBlank()) {
      return null;
    }

    var explicitTitle = attrs.title();
    var label = (explicitTitle != null && !explicitTitle.isBlank()) ? explicitTitle : url;
    return "[%s](%s)".formatted(MarkdownText.escapeLinkText(label), url);
  }
}
