package dev.nthings.adf4j;

import java.util.Objects;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.engine.AdfDocumentWorkflow;
import dev.nthings.adf4j.internal.engine.AdfParsingService;
import dev.nthings.adf4j.internal.engine.AdfServices;

/**
 * Parses Atlassian Document Format (ADF) JSON, renders it to Markdown, and extracts
 * {@link ContentMetadata}. Immutable and thread-safe; for the zero-config case use {@link Adf}.
 */
public final class AdfConverter {

  private final AdfParsingService parsingService;
  private final AdfDocumentWorkflow workflow;

  public AdfConverter() {
    this(AdfServices.createDefault());
  }

  AdfConverter(AdfServices services) {
    this.parsingService = services.parsingService();
    this.workflow = services.workflow();
  }

  public ParseResult parse(String adfJson) {
    return parsingService.parse(adfJson);
  }

  public RenderResult render(AdfDocument document, RenderOptions options) {
    return workflow.render(document, requireOptions(options));
  }

  public RenderResult render(String adfJson, RenderOptions options) {
    return workflow.render(adfJson, requireOptions(options));
  }

  public String toMarkdown(AdfDocument document, RenderOptions options) {
    return workflow.toMarkdown(document, requireOptions(options));
  }

  public String toMarkdown(String adfJson, RenderOptions options) {
    return workflow.toMarkdown(adfJson, requireOptions(options));
  }

  public ContentMetadata metadata(AdfDocument document, RenderOptions options) {
    return workflow.metadata(document, requireOptions(options));
  }

  private static RenderOptions requireOptions(RenderOptions options) {
    return Objects.requireNonNull(options, "options");
  }
}
