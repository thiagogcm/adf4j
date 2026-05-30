package dev.nthings.adf4j.renderer;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.jsoup.nodes.Entities;

public final class MarkdownRenderingSupport {

  private final Parser markdownParser;
  private final MarkdownRenderer markdownRenderer;
  private final HtmlRenderer htmlRenderer;

  MarkdownRenderingSupport(
      Parser markdownParser, MarkdownRenderer markdownRenderer, HtmlRenderer htmlRenderer) {
    this.markdownParser = markdownParser;
    this.markdownRenderer = markdownRenderer;
    this.htmlRenderer = htmlRenderer;
  }

  public String normalizeMarkdown(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return "";
    }
    var cleaned = MarkdownInputCleaner.clean(markdown);
    return markdownRenderer.render(markdownParser.parse(cleaned)).stripTrailing();
  }

  public String renderHtmlDocument(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return "";
    }
    var cleaned = MarkdownInputCleaner.clean(markdown);
    return htmlRenderer.render(markdownParser.parse(cleaned)).stripTrailing();
  }

  String renderHtmlFragment(String markdown) {
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
