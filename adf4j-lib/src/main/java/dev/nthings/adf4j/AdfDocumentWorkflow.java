package dev.nthings.adf4j;

import java.util.List;
import java.util.Objects;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.model.ParseIssue;
import dev.nthings.adf4j.renderer.AdfContentMetadataExtractor;
import dev.nthings.adf4j.renderer.AdfHeadingCollector;
import dev.nthings.adf4j.renderer.AdfRenderer;
import dev.nthings.adf4j.renderer.MarkdownRenderingSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AdfDocumentWorkflow {

  private static final Logger log = LoggerFactory.getLogger(AdfDocumentWorkflow.class);

  private final AdfParsingService parsingService;
  private final AdfHeadingCollector headingCollector;
  private final AdfRenderer renderer;
  private final AdfContentMetadataExtractor contentMetadataExtractor;
  private final MarkdownRenderingSupport markdownRenderingSupport;

  AdfDocumentWorkflow(
      AdfParsingService parsingService,
      AdfHeadingCollector headingCollector,
      AdfRenderer renderer,
      AdfContentMetadataExtractor contentMetadataExtractor,
      MarkdownRenderingSupport markdownRenderingSupport) {
    this.parsingService = parsingService;
    this.headingCollector = headingCollector;
    this.renderer = renderer;
    this.contentMetadataExtractor = contentMetadataExtractor;
    this.markdownRenderingSupport = markdownRenderingSupport;
  }

  RenderResult render(String rawAdf, RenderOptions options) {
    Objects.requireNonNull(options, "options");
    if (rawAdf == null || rawAdf.isBlank()) {
      log.debug("render called with null or blank input – returning empty result");
      return RenderResult.empty();
    }

    var parsed = parsingService.parse(rawAdf);
    if (parsed.document() == null || !parsed.validAdfRoot()) {
      log.warn("Input is not a valid ADF document – treating as raw markdown");
      var body = markdownRenderingSupport.normalizeMarkdown(rawAdf);
      return new RenderResult(body, ContentMetadata.empty(), parsed.issues());
    }

    return render(parsed.document(), options, parsed.issues());
  }

  RenderResult render(AdfDocument document, RenderOptions options) {
    return render(document, options, List.of());
  }

  private RenderResult render(
      AdfDocument document, RenderOptions options, List<ParseIssue> parseDiagnostics) {
    var requiredOptions = Objects.requireNonNull(options, "options");
    log.debug("Rendering markdown from AST");

    var outline = headingCollector.collect(document);
    var body = renderer.render(document, requiredOptions, outline);
    var metadata = contentMetadataExtractor.extract(document, requiredOptions, outline.headings());
    return new RenderResult(body, metadata, parseDiagnostics);
  }
}
