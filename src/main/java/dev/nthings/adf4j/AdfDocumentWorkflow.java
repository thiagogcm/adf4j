package dev.nthings.adf4j;

import java.util.List;
import java.util.Objects;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.model.ParseIssue;
import dev.nthings.adf4j.renderer.AdfContentMetadataExtractor;
import dev.nthings.adf4j.renderer.AdfHeadingCollector;
import dev.nthings.adf4j.renderer.AdfRenderer;
import dev.nthings.adf4j.renderer.MarkdownRenderingSupport;
import dev.nthings.adf4j.renderer.RenderingStrategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AdfDocumentWorkflow {

  private static final Logger log = LoggerFactory.getLogger(AdfDocumentWorkflow.class);

  private final AdfParsingService parsingService;
  private final AdfHeadingCollector headingCollector;
  private final AdfRenderer renderer;
  private final AdfContentMetadataExtractor contentMetadataExtractor;
  private final MarkdownRenderingSupport markdownRenderingSupport;
  private final PresentationHtmlSanitizer htmlSanitizer;

  AdfDocumentWorkflow(
      AdfParsingService parsingService,
      AdfHeadingCollector headingCollector,
      AdfRenderer renderer,
      AdfContentMetadataExtractor contentMetadataExtractor,
      MarkdownRenderingSupport markdownRenderingSupport,
      PresentationHtmlSanitizer htmlSanitizer) {
    this.parsingService = parsingService;
    this.headingCollector = headingCollector;
    this.renderer = renderer;
    this.contentMetadataExtractor = contentMetadataExtractor;
    this.markdownRenderingSupport = markdownRenderingSupport;
    this.htmlSanitizer = htmlSanitizer;
  }

  RenderResult render(String rawAdf, RenderOptions options, OutputFormat outputFormat) {
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(outputFormat, "outputFormat");
    if (rawAdf == null || rawAdf.isBlank()) {
      log.debug("render called with null or blank input – returning empty {} result", outputFormat);
      return RenderResult.empty(outputFormat);
    }

    var parsed = parsingService.parse(rawAdf);
    if (parsed.document() == null || !parsed.validAdfRoot()) {
      log.warn("Input is not a valid ADF document – treating as raw markdown ({} mode)", outputFormat);
      var body = switch (outputFormat) {
        case STORAGE_MARKDOWN, PRESENTATION_MARKDOWN -> markdownRenderingSupport.normalizeMarkdown(rawAdf);
        case PRESENTATION_HTML -> renderHtmlFromMarkdown(rawAdf);
      };
      return new RenderResult(body, outputFormat, ContentMetadata.empty(), parsed.issues());
    }

    return render(parsed.document(), options, outputFormat, parsed.issues());
  }

  RenderResult render(AdfDocument document, RenderOptions options, OutputFormat outputFormat) {
    return render(document, options, outputFormat, List.of());
  }

  String renderHtmlFromMarkdown(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return "";
    }
    return htmlSanitizer
        .sanitize(markdownRenderingSupport.renderHtmlDocument(markdown))
        .stripTrailing();
  }

  String normalizeMarkdown(String markdown) {
    return markdownRenderingSupport.normalizeMarkdown(markdown);
  }

  private RenderResult render(
      AdfDocument document,
      RenderOptions options,
      OutputFormat outputFormat,
      List<ParseIssue> parseDiagnostics) {
    var requiredOptions = Objects.requireNonNull(options, "options");
    Objects.requireNonNull(outputFormat, "outputFormat");
    log.debug("Rendering {} from AST", outputFormat);

    var renderingStrategy = switch (outputFormat) {
      case STORAGE_MARKDOWN -> RenderingStrategies.storage();
      case PRESENTATION_MARKDOWN, PRESENTATION_HTML -> RenderingStrategies.presentation();
    };

    var outline = headingCollector.collect(document);
    var markdownBody = renderer.render(document, requiredOptions, renderingStrategy, outline);
    var body = outputFormat == OutputFormat.PRESENTATION_HTML
        ? renderHtmlFromMarkdown(markdownBody)
        : markdownBody;
    var metadata = contentMetadataExtractor.extract(document, requiredOptions, outline.headings());
    return new RenderResult(body, outputFormat, metadata, parseDiagnostics);
  }
}
