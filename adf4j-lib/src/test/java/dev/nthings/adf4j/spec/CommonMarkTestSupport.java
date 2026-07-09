package dev.nthings.adf4j.spec;

import dev.nthings.adf4j.internal.render.CommonMarkSupport;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;

final class CommonMarkTestSupport {

  private static final Parser PARSER = CommonMarkSupport.parser();
  private static final HtmlRenderer HTML_RENDERER = CommonMarkSupport.htmlRenderer(false);
  private static final MarkdownRenderer MARKDOWN_RENDERER = CommonMarkSupport.markdownRenderer();

  private CommonMarkTestSupport() {}

  static String toHtml(String markdown) {
    return HTML_RENDERER.render(PARSER.parse(markdown));
  }

  static String roundTripMarkdown(String markdown) {
    return MARKDOWN_RENDERER.render(PARSER.parse(markdown));
  }

  static String roundTripToHtml(String markdown) {
    return toHtml(roundTripMarkdown(markdown));
  }
}
