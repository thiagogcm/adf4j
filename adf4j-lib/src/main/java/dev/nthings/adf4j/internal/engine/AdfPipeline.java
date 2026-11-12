package dev.nthings.adf4j.internal.engine;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.analyze.AdfDocumentAnalyzer;
import dev.nthings.adf4j.internal.parser.AdfParsingService;
import dev.nthings.adf4j.internal.render.AdfRenderer;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.result.AdfException;
import dev.nthings.adf4j.result.AnalysisResult;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.ParseResult;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Runs the `parse → analyze → render` pipeline behind `AdfToMarkdown`. Stateless and
/// thread-safe, so one instance serves the process.
public final class AdfPipeline {

  private final AdfParsingService parsingService;
  private final AdfDocumentAnalyzer analyzer;
  private final AdfRenderer renderer;

  private AdfPipeline(
      AdfParsingService parsingService, AdfDocumentAnalyzer analyzer, AdfRenderer renderer) {
    this.parsingService = parsingService;
    this.analyzer = analyzer;
    this.renderer = renderer;
  }

  public static AdfPipeline createDefault() {
    return new AdfPipeline(
        AdfParsingService.createDefault(),
        AdfDocumentAnalyzer.createDefault(),
        AdfRenderer.createDefault());
  }

  public AnalysisResult analyze(ParseResult parsed, ConversionOptions options) {
    var document = parsed.document();
    if (document == null) {
      return AnalysisResult.failed(parsed.diagnostics());
    }
    var result = analyze(document, options);
    return new AnalysisResult(
        result.metadata(), mergeDiagnostics(parsed.diagnostics(), result.diagnostics()));
  }

  public AnalysisResult analyze(AdfDocument document, ConversionOptions options) {
    var attachments = AttachmentInventory.from(options.attachments());
    var result = analyzer.analyze(document, options.unknownNodePolicy(), attachments);
    return new AnalysisResult(result.facts().metadata(), result.diagnostics());
  }

  public MarkdownResult convert(@Nullable String adfJson, ConversionOptions options) {
    return convert(parsingService.parse(adfJson), options);
  }

  public MarkdownResult convert(ParseResult parsed, ConversionOptions options) {
    return render(parsed.document(), options, parsed.diagnostics());
  }

  public MarkdownResult convert(AdfDocument document, ConversionOptions options) {
    return render(document, options, List.of());
  }

  // Failed parsing yields an empty body, including when a title is configured.
  private MarkdownResult render(
      @Nullable AdfDocument document, ConversionOptions options, List<Diagnostic> diagnostics) {
    if (document == null) {
      return MarkdownResult.failed(diagnostics);
    }
    var attachments = AttachmentInventory.from(options.attachments());
    var facts = analyzer.facts(document, options.unknownNodePolicy(), attachments);
    try {
      var rendered = renderer.render(document, options, attachments, facts.outline());
      // Only visited content contributes render diagnostics.
      var merged = mergeDiagnostics(diagnostics, rendered.diagnostics());
      return new MarkdownResult(
          rendered.body(), facts.metadata(), merged, rendered.unresolved(), rendered.effects());
    } catch (AdfException failure) {
      var issues = mergeDiagnostics(diagnostics, failure.diagnostics());
      return new MarkdownResult("", facts.metadata(), issues, List.of(), List.of());
    }
  }

  private static List<Diagnostic> mergeDiagnostics(
      List<Diagnostic> earlier, List<Diagnostic> later) {
    if (later.isEmpty()) {
      return earlier;
    }
    var merged = new ArrayList<Diagnostic>(earlier.size() + later.size());
    merged.addAll(earlier);
    merged.addAll(later);
    return merged;
  }
}
