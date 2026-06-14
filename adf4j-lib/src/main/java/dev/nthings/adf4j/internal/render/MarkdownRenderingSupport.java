package dev.nthings.adf4j.internal.render;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.nodes.Entities;
import org.jspecify.annotations.Nullable;

/** Renders Markdown fragments to inline HTML for table cells GFM can't express (colspan/rowspan). */
final class MarkdownRenderingSupport {

  private final Parser markdownParser;
  private final HtmlRenderer htmlRenderer;

  MarkdownRenderingSupport(Parser markdownParser, HtmlRenderer htmlRenderer) {
    this.markdownParser = markdownParser;
    this.htmlRenderer = htmlRenderer;
  }

  private String renderHtmlDocument(@Nullable String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return "";
    }
    var cleaned = MarkdownInputCleaner.clean(markdown);
    return htmlRenderer.render(markdownParser.parse(cleaned)).stripTrailing();
  }

  String renderHtmlFragment(@Nullable String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return "";
    }

    var html = renderHtmlDocument(markdown).strip();
    if (html.isBlank()) {
      return Entities.escape(MarkdownInputCleaner.clean(markdown)).replace("\n", "<br>");
    }

    if (html.startsWith("<p>") && html.endsWith("</p>")) {
      return html.substring(3, html.length() - 4).replace("\n", "<br>");
    }

    return html;
  }
}
