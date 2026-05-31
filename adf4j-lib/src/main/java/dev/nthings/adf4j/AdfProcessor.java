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

  public RenderResult render(String rawAdf, RenderOptions options) {
    return workflow.render(rawAdf, Objects.requireNonNull(options, "options"));
  }

  public RenderResult render(AdfDocument document, RenderOptions options) {
    return workflow.render(document, Objects.requireNonNull(options, "options"));
  }

  public String renderMarkdown(String rawAdf) {
    return renderMarkdown(rawAdf, RenderOptions.defaults());
  }

  public String renderMarkdown(String rawAdf, RenderOptions options) {
    return render(rawAdf, Objects.requireNonNull(options, "options")).body();
  }

  public String renderMarkdown(AdfDocument document, RenderOptions options) {
    return render(document, Objects.requireNonNull(options, "options")).body();
  }

  public ContentMetadata extractContentMetadata(String rawAdf, RenderOptions options) {
    return render(rawAdf, Objects.requireNonNull(options, "options")).metadata();
  }

  public ContentMetadata extractContentMetadata(AdfDocument document, RenderOptions options) {
    return render(document, Objects.requireNonNull(options, "options")).metadata();
  }
}
