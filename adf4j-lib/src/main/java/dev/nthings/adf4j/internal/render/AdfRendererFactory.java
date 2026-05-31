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

/** Wires the Markdown rendering pipeline, keeping the CommonMark dependency a render-side detail. */
public final class AdfRendererFactory {

  private AdfRendererFactory() {
  }

  public static AdfRenderer adfRenderer(AdfHeadingCollector headingCollector) {
    return new AdfRenderer(
        headingCollector,
        new TextMarkRenderer(),
        new ListRenderer(),
        new TableRenderer(markdownRenderingSupport()),
        new MediaRenderer(),
        new MacroRenderer(),
        new CardRenderer());
  }

  public static AdfContentMetadataExtractor contentMetadataExtractor() {
    return new AdfContentMetadataExtractor();
  }

  private static MarkdownRenderingSupport markdownRenderingSupport() {
    var extensions = commonmarkExtensions();
    var parser = Parser.builder().extensions(extensions).build();
    var htmlRenderer = HtmlRenderer.builder().extensions(extensions).sanitizeUrls(false).build();
    return new MarkdownRenderingSupport(parser, htmlRenderer);
  }

  private static List<Extension> commonmarkExtensions() {
    return List.of(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
        HeadingAnchorExtension.create(),
        ImageAttributesExtension.create(),
        AlertsExtension.create());
  }
}
