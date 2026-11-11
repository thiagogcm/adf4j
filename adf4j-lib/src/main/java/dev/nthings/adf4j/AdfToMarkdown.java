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

/// Converts ADF to Markdown and analyzes its metadata. Immutable and cheap to create. Expected
/// input failures return diagnostics; `convertOrThrow` and `toMarkdown` throw AdfException.
/// Callback thread safety is the caller's responsibility.
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

  /// Blank or invalid input yields an empty body and error diagnostics.
  public MarkdownResult convert(@Nullable String json) {
    return PIPELINE.convert(json, options);
  }

  /// Keeps the parse diagnostics in the result.
  public MarkdownResult convert(ParseResult parsed) {
    return PIPELINE.convert(Objects.requireNonNull(parsed), options);
  }

  public MarkdownResult convert(AdfDocument document) {
    return PIPELINE.convert(Objects.requireNonNull(document), options);
  }

  /// Renders excerpt content without the document title.
  public MarkdownResult convert(ExcerptDefinition excerpt) {
    return PIPELINE.convert(new AdfDocument(1, excerpt.content()), options.withDocumentTitle(null));
  }

  /// Extracts metadata without invoking render callbacks.
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

  /// The Markdown body; throws AdfException on failure.
  public String toMarkdown(@Nullable String json) {
    return convert(json).requireBody();
  }
}
