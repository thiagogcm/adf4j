package dev.nthings.adf4j;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.engine.AdfPipeline;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.result.AnalysisResult;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.ParseResult;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Converts ADF to Markdown and extracts source metadata. Immutable and cheap to create.
/// Expected input failures return diagnostics; string-only and explicitly throwing methods
/// raise AdfException on failure. Callback thread safety is the caller's responsibility.
public final class AdfToMarkdown {
  private static final AdfPipeline PIPELINE = AdfPipeline.createDefault();
  private final ConversionOptions options;

  private AdfToMarkdown(ConversionOptions options) {
    this.options = Objects.requireNonNull(options, "options");
  }

  public static AdfToMarkdown create() {
    return with(ConversionOptions.defaults());
  }

  public static AdfToMarkdown with(ConversionOptions options) {
    return new AdfToMarkdown(options);
  }

  /// Expected input failures have an empty body and error diagnostics.
  public MarkdownResult convert(@Nullable String json) {
    return PIPELINE.convert(json, options);
  }

  /// Reuses a parsed document while preserving its parse diagnostics.
  public MarkdownResult convert(ParseResult parsed) {
    return PIPELINE.convert(Objects.requireNonNull(parsed), options);
  }

  public MarkdownResult convert(AdfDocument document) {
    return PIPELINE.convert(Objects.requireNonNull(document), options);
  }

  /// Renders excerpt content without the document's title.
  public MarkdownResult convert(ExcerptDefinition excerpt) {
    return PIPELINE.convert(new AdfDocument(1, excerpt.content()), options.withDocumentTitle(null));
  }

  /// Extracts source references and outline without invoking render callbacks.
  public AnalysisResult analyze(String json) {
    return analyze(AdfJson.parse(json));
  }

  public AnalysisResult analyze(ParseResult parsed) {
    return PIPELINE.analyze(Objects.requireNonNull(parsed), options);
  }

  public AnalysisResult analyze(AdfDocument document) {
    return PIPELINE.analyze(Objects.requireNonNull(document), options);
  }

  public MarkdownResult convertOrThrow(String json) {
    var result = convert(json);
    result.requireSuccess();
    return result;
  }

  /// The Markdown body, or AdfException when conversion failed.
  public String toMarkdown(@Nullable String json) {
    return convert(json).requireBody();
  }
}
