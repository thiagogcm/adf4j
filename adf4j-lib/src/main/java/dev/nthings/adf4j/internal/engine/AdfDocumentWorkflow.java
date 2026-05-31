package dev.nthings.adf4j.internal.engine;

import java.util.List;

import dev.nthings.adf4j.ContentMetadata;
import dev.nthings.adf4j.ParseIssue;
import dev.nthings.adf4j.RenderOptions;
import dev.nthings.adf4j.RenderResult;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.render.AdfContentMetadataExtractor;
import dev.nthings.adf4j.internal.render.AdfHeadingCollector;
import dev.nthings.adf4j.internal.render.AdfRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AdfDocumentWorkflow {

  private static final Logger log = LoggerFactory.getLogger(AdfDocumentWorkflow.class);

  private final AdfParsingService parsingService;
  private final AdfHeadingCollector headingCollector;
  private final AdfRenderer renderer;
  private final AdfContentMetadataExtractor contentMetadataExtractor;

  public AdfDocumentWorkflow(
      AdfParsingService parsingService,
      AdfHeadingCollector headingCollector,
      AdfRenderer renderer,
      AdfContentMetadataExtractor contentMetadataExtractor) {
    this.parsingService = parsingService;
    this.headingCollector = headingCollector;
    this.renderer = renderer;
    this.contentMetadataExtractor = contentMetadataExtractor;
  }

  public RenderResult render(String rawAdf, RenderOptions options) {
    if (rawAdf == null || rawAdf.isBlank()) {
      log.debug("render called with null or blank input – returning empty result");
      return RenderResult.empty();
    }

    var parsed = parsingService.parse(rawAdf);
    if (parsed.document() == null || !parsed.validAdfRoot()) {
      log.warn("Input is not a valid ADF document – returning empty result with diagnostics");
      return new RenderResult("", ContentMetadata.empty(), parsed.issues());
    }

    return render(parsed.document(), options, parsed.issues());
  }

  public RenderResult render(AdfDocument document, RenderOptions options) {
    return render(document, options, List.of());
  }

  public String toMarkdown(String rawAdf, RenderOptions options) {
    if (rawAdf == null || rawAdf.isBlank()) {
      return "";
    }
    var parsed = parsingService.parse(rawAdf);
    if (parsed.document() == null || !parsed.validAdfRoot()) {
      return "";
    }
    return toMarkdown(parsed.document(), options);
  }

  public String toMarkdown(AdfDocument document, RenderOptions options) {
    if (document == null) {
      return "";
    }
    return renderer.render(document, options, headingCollector.collect(document));
  }

  public ContentMetadata metadata(AdfDocument document, RenderOptions options) {
    if (document == null) {
      return ContentMetadata.empty();
    }
    var outline = headingCollector.collect(document);
    return contentMetadataExtractor.extract(document, options, outline.headings());
  }

  private RenderResult render(
      AdfDocument document, RenderOptions options, List<ParseIssue> parseDiagnostics) {
    log.debug("Rendering markdown from AST");

    var outline = headingCollector.collect(document);
    var body = renderer.render(document, options, outline);
    var metadata = contentMetadataExtractor.extract(document, options, outline.headings());
    return new RenderResult(body, metadata, parseDiagnostics);
  }
}
