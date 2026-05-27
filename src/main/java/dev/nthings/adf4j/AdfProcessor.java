package dev.nthings.adf4j;

import java.util.Objects;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.model.ParseResult;

public final class AdfProcessor {

  private final AdfParsingService parsingService;
  private final AdfDocumentWorkflow workflow;

  public AdfProcessor() {
    this(AdfServices.createDefault());
  }

  AdfProcessor(AdfServices services) {
    this(services.parsingService(), services.workflow());
  }

  AdfProcessor(AdfParsingService parsingService, AdfDocumentWorkflow workflow) {
    this.parsingService = Objects.requireNonNull(parsingService, "parsingService");
    this.workflow = Objects.requireNonNull(workflow, "workflow");
  }

  public ParseResult parse(String rawAdf) {
    return parsingService.parse(rawAdf);
  }

  public RenderResult renderStorageDocument(String rawAdf, RenderOptions options) {
    return workflow.render(rawAdf, Objects.requireNonNull(options, "options"), OutputFormat.STORAGE_MARKDOWN);
  }

  public String renderStorageMarkdown(String rawAdf) {
    return renderStorageMarkdown(rawAdf, RenderOptions.defaults(""));
  }

  public String renderStorageMarkdown(String rawAdf, RenderOptions options) {
    return renderStorageDocument(rawAdf, Objects.requireNonNull(options, "options")).body();
  }

  public String renderStorageMarkdown(AdfDocument document, RenderOptions options) {
    return workflow
        .render(document, Objects.requireNonNull(options, "options"), OutputFormat.STORAGE_MARKDOWN)
        .body();
  }

  public ContentMetadata extractContentMetadata(String rawAdf, RenderOptions options) {
    return renderStorageDocument(rawAdf, Objects.requireNonNull(options, "options")).metadata();
  }

  public ContentMetadata extractContentMetadata(AdfDocument document, RenderOptions options) {
    return workflow
        .render(document, Objects.requireNonNull(options, "options"), OutputFormat.STORAGE_MARKDOWN)
        .metadata();
  }

  public String renderPresentationMarkdown(String rawAdf, RenderOptions options) {
    return workflow
        .render(rawAdf, Objects.requireNonNull(options, "options"), OutputFormat.PRESENTATION_MARKDOWN)
        .body();
  }

  public String renderPresentationMarkdown(AdfDocument document, RenderOptions options) {
    return workflow
        .render(document, Objects.requireNonNull(options, "options"), OutputFormat.PRESENTATION_MARKDOWN)
        .body();
  }

  public String renderPresentationHtml(String rawAdf, RenderOptions options) {
    return workflow
        .render(rawAdf, Objects.requireNonNull(options, "options"), OutputFormat.PRESENTATION_HTML)
        .body();
  }
}
