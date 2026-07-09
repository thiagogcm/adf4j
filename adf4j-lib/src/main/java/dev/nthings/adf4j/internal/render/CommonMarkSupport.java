package dev.nthings.adf4j.internal.render;

import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.alerts.AlertsExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.image.attributes.ImageAttributesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;

/// Central CommonMark wiring for adf4j's generated Markdown dialect.
public final class CommonMarkSupport {

  private static final List<Extension> EXTENSIONS =
      List.of(
          TablesExtension.create(),
          StrikethroughExtension.create(),
          TaskListItemsExtension.create(),
          HeadingAnchorExtension.create(),
          ImageAttributesExtension.create(),
          AlertsExtension.builder().allowNestedAlerts(true).build());

  private CommonMarkSupport() {}

  public static Parser parser() {
    return Parser.builder().extensions(EXTENSIONS).build();
  }

  public static HtmlRenderer htmlRenderer(boolean sanitizeUrls) {
    return HtmlRenderer.builder().extensions(EXTENSIONS).sanitizeUrls(sanitizeUrls).build();
  }

  public static MarkdownRenderer markdownRenderer() {
    return MarkdownRenderer.builder().extensions(EXTENSIONS).build();
  }

  static MarkdownRenderingSupport markdownRenderingSupport() {
    // Defence-in-depth for the HTML-table fallback (destinations are already scheme-sanitized
    // upstream).
    return new MarkdownRenderingSupport(parser(), htmlRenderer(true));
  }
}
