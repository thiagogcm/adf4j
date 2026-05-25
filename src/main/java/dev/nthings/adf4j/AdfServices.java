package dev.nthings.adf4j;

import java.util.List;

import dev.nthings.adf4j.parser.AdfAstParser;
import dev.nthings.adf4j.renderer.AdfContentMetadataExtractor;
import dev.nthings.adf4j.renderer.AdfHeadingCollector;
import dev.nthings.adf4j.renderer.AdfRendererFactory;

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

import tools.jackson.databind.json.JsonMapper;

final class AdfServices {

  private final JsonMapper mapper;
  private final AdfAstParser astParser;
  private final AdfParsingService parsingService;
  private final AdfContentMetadataExtractor metadataExtractor;
  private final AdfStorageDocumentService storageDocumentService;
  private final AdfPresentationDocumentService presentationDocumentService;

  private AdfServices(
      JsonMapper mapper,
      AdfAstParser astParser,
      AdfParsingService parsingService,
      AdfContentMetadataExtractor metadataExtractor,
      AdfStorageDocumentService storageDocumentService,
      AdfPresentationDocumentService presentationDocumentService) {
    this.mapper = mapper;
    this.astParser = astParser;
    this.parsingService = parsingService;
    this.metadataExtractor = metadataExtractor;
    this.storageDocumentService = storageDocumentService;
    this.presentationDocumentService = presentationDocumentService;
  }

  static AdfServices createDefault() {
    var mapper = JsonMapper.builder().build();
    var astParser = new AdfAstParser(mapper);
    var commonmarkExtensions = adfCommonmarkExtensions();
    var markdownParser = Parser.builder().extensions(commonmarkExtensions).build();
    var markdownRenderer = MarkdownRenderer.builder().extensions(commonmarkExtensions).build();
    var htmlRenderer = HtmlRenderer.builder().extensions(commonmarkExtensions).sanitizeUrls(false).build();
    var markdownRenderingSupport = AdfRendererFactory.markdownRenderingSupport(
        markdownParser, markdownRenderer, htmlRenderer);
    var headingCollector = new AdfHeadingCollector();
    var renderer = AdfRendererFactory.adfRenderer(markdownRenderingSupport, headingCollector);
    var metadataExtractor = AdfRendererFactory.contentMetadataExtractor(headingCollector);
    var parsingService = new AdfParsingService(mapper, astParser);
    var htmlSanitizer = new PresentationHtmlSanitizer();
    var workflow = new AdfDocumentWorkflow(
        parsingService,
        headingCollector,
        renderer,
        metadataExtractor,
        markdownRenderingSupport,
        htmlSanitizer);
    var storageDocumentService = new AdfStorageDocumentService(workflow);
    var presentationDocumentService = new AdfPresentationDocumentService(workflow);
    return new AdfServices(
        mapper,
        astParser,
        parsingService,
        metadataExtractor,
        storageDocumentService,
        presentationDocumentService);
  }

  JsonMapper mapper() {
    return mapper;
  }

  AdfAstParser astParser() {
    return astParser;
  }

  AdfParsingService parsingService() {
    return parsingService;
  }

  AdfContentMetadataExtractor metadataExtractor() {
    return metadataExtractor;
  }

  AdfStorageDocumentService storageDocumentService() {
    return storageDocumentService;
  }

  AdfPresentationDocumentService presentationDocumentService() {
    return presentationDocumentService;
  }

  private static List<Extension> adfCommonmarkExtensions() {
    return List.of(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
        HeadingAnchorExtension.create(),
        ImageAttributesExtension.create(),
        AlertsExtension.create());
  }
}
