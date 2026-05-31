package dev.nthings.adf4j.renderer;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;

public final class AdfRendererFactory {

  private AdfRendererFactory() {
  }

  public static MarkdownRenderingSupport markdownRenderingSupport(
      Parser markdownParser, MarkdownRenderer markdownRenderer, HtmlRenderer htmlRenderer) {
    return new MarkdownRenderingSupport(markdownParser, markdownRenderer, htmlRenderer);
  }

  public static AdfRenderer adfRenderer(
      MarkdownRenderingSupport markdownRenderingSupport, AdfHeadingCollector headingCollector) {
    return new AdfRenderer(
        headingCollector,
        new TextMarkRenderer(),
        new ListRenderer(),
        new TableRenderer(markdownRenderingSupport),
        new MediaRenderer(),
        new MacroRenderer(),
        new CardRenderer());
  }

  public static AdfContentMetadataExtractor contentMetadataExtractor(
      AdfHeadingCollector headingCollector) {
    return new AdfContentMetadataExtractor(headingCollector);
  }
}
