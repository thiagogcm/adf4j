package dev.nthings.adf4j;

import java.util.Objects;

import dev.nthings.adf4j.ast.AdfDocument;

public class AdfStorageDocumentService {

  private final AdfDocumentWorkflow workflow;

  public AdfStorageDocumentService(AdfDocumentWorkflow workflow) {
    this.workflow = workflow;
  }

  public RenderResult renderStorageDocument(String rawAdf, RenderOptions options) {
    return workflow.render(rawAdf, Objects.requireNonNull(options, "options"), OutputFormat.STORAGE_MARKDOWN);
  }

  public String renderStorageMarkdown(String rawAdf, RenderOptions options) {
    return renderStorageDocument(rawAdf, options).body();
  }

  public String renderStorageMarkdown(AdfDocument document, RenderOptions options) {
    return workflow
        .render(document, Objects.requireNonNull(options, "options"), OutputFormat.STORAGE_MARKDOWN)
        .body();
  }

  public String normalizeStorageMarkdown(String markdown) {
    return workflow.normalizeMarkdown(markdown);
  }

  public ContentMetadata extractContentMetadata(String rawAdf, RenderOptions options) {
    return renderStorageDocument(rawAdf, Objects.requireNonNull(options, "options")).metadata();
  }

  public ContentMetadata extractContentMetadata(AdfDocument document, RenderOptions options) {
    return workflow
        .render(document, Objects.requireNonNull(options, "options"), OutputFormat.STORAGE_MARKDOWN)
        .metadata();
  }
}
