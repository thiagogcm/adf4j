package dev.nthings.adf4j.internal.render;

import org.commonmark.node.Paragraph;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.nodes.Entities;

/// Markdown fragments to inline HTML, for cells GFM can't express (colspan/rowspan).
final class CellHtmlRenderer {

  private final Parser markdownParser;
  private final HtmlRenderer htmlRenderer;

  CellHtmlRenderer(Parser markdownParser, HtmlRenderer htmlRenderer) {
    this.markdownParser = markdownParser;
    this.htmlRenderer = htmlRenderer;
  }

  /// Inline HTML for a fragment; a lone paragraph loses its `<p>` wrapper.
  String render(String markdown) {
    var cleaned = MarkdownInputCleaner.clean(markdown);
    var document = markdownParser.parse(cleaned);
    var html = htmlRenderer.render(document).strip();
    if (html.isBlank()) {
      return Entities.escape(cleaned).replace("\n", "<br>");
    }

    var first = document.getFirstChild();
    if (first instanceof Paragraph && first == document.getLastChild()) {
      return html.substring("<p>".length(), html.length() - "</p>".length()).replace("\n", "<br>");
    }

    return html;
  }
}
