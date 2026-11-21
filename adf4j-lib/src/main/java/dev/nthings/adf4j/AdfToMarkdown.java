package dev.nthings.adf4j;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.engine.AdfPipeline;
import dev.nthings.adf4j.internal.render.AdfDocumentRenderer;
import dev.nthings.adf4j.internal.render.markdown.MarkdownAdfRenderer;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.DocumentContext;
import dev.nthings.adf4j.result.AnalysisResult;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.ParseResult;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Converts ADF to Markdown and analyzes its metadata. Immutable and cheap to create. Expected
/// input failures return diagnostics; `convertOrThrow` and `toMarkdown` throw AdfException.
/// Callback thread safety is the caller's responsibility.
public final class AdfToMarkdown {
  private static final AdfPipeline PIPELINE = AdfPipeline.createDefault();
  private static final MarkdownAdfRenderer RENDERER = MarkdownAdfRenderer.createDefault();

  private final ConversionOptions options;
  // Bound once; the title comes from the per-call DocumentContext, so excerpts can share it.
  private final AdfDocumentRenderer renderer;

  private AdfToMarkdown(ConversionOptions options) {
    this.options = Objects.requireNonNull(options, "options");
    this.renderer = RENDERER.forOptions(options);
  }

  public static AdfToMarkdown create() {
    return with(ConversionOptions.defaults());
  }

  public static AdfToMarkdown with(ConversionOptions options) {
    return new AdfToMarkdown(options);
  }

  /// Blank or invalid input yields an empty body and error diagnostics.
  public MarkdownResult convert(@Nullable String json) {
    return convert(AdfJson.parse(json));
  }

  /// Keeps the parse diagnostics in the result.
  public MarkdownResult convert(ParseResult parsed) {
    return render(Objects.requireNonNull(parsed), options.context());
  }

  public MarkdownResult convert(AdfDocument document) {
    return render(new ParseResult(Objects.requireNonNull(document), List.of()), options.context());
  }

  /// Renders excerpt content without the document title.
  public MarkdownResult convert(ExcerptDefinition excerpt) {
    var document = new AdfDocument(1, excerpt.content());
    return render(new ParseResult(document, List.of()), options.withDocumentTitle(null).context());
  }

  private MarkdownResult render(ParseResult parsed, DocumentContext context) {
    return PIPELINE.render(
        parsed,
        context,
        options.unknownNodePolicy(),
        options.resourceResolvers(),
        renderer,
        MarkdownResult::new);
  }

  /// Extracts metadata without invoking render callbacks.
  public AnalysisResult analyze(String json) {
    return analyze(AdfJson.parse(json));
  }

  public AnalysisResult analyze(ParseResult parsed) {
    return PIPELINE.analyze(
        Objects.requireNonNull(parsed), options.context(), options.unknownNodePolicy());
  }

  public AnalysisResult analyze(AdfDocument document) {
    return PIPELINE.analyze(
        Objects.requireNonNull(document), options.context(), options.unknownNodePolicy());
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
