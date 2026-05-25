package dev.nthings.adf4j;

import java.util.Objects;

import dev.nthings.adf4j.ast.AdfDocument;

public class AdfPresentationDocumentService {

  private final AdfDocumentWorkflow workflow;

  public AdfPresentationDocumentService(AdfDocumentWorkflow workflow) {
    this.workflow = workflow;
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

  public String renderHtmlFromMarkdown(String markdown) {
    return workflow.renderHtmlFromMarkdown(markdown);
  }
}
