package dev.nthings.adf4j.internal.engine;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.analyze.AdfDocumentAnalyzer;
import dev.nthings.adf4j.internal.parser.AdfParsingService;
import dev.nthings.adf4j.internal.render.AdfRenderer;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.ParseResult;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Assembles and runs the `parse → analyze → render` pipeline behind
/// {@link AdfToMarkdown}. Stateless and thread-safe, so one instance serves the whole process; each
/// phase owns its own construction via `createDefault()`.
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

  public ContentMetadata analyze(ParseResult parsed, MarkdownOptions options) {
    var document = parsed.document();
    return document == null ? ContentMetadata.empty() : analyze(document, options);
  }

  public ContentMetadata analyze(AdfDocument document, MarkdownOptions options) {
    var attachments = AttachmentInventory.from(options.attachments());
    return analyzer.analyze(document, options, attachments).metadata();
  }

  public MarkdownResult convert(@Nullable String adfJson, MarkdownOptions options) {
    return convert(parsingService.parse(adfJson), options);
  }

  public MarkdownResult convert(ParseResult parsed, MarkdownOptions options) {
    return render(parsed.document(), options, parsed.diagnostics());
  }

  public MarkdownResult convert(AdfDocument document, MarkdownOptions options) {
    return render(document, options, List.of());
  }

  // A null document yields an empty (possibly titled) body.
  private MarkdownResult render(
      @Nullable AdfDocument document, MarkdownOptions options, List<Diagnostic> diagnostics) {
    var attachments = AttachmentInventory.from(options.attachments());
    var analysis = analyzer.analyze(document, options, attachments);
    var rendered = renderer.render(document, options, attachments, analysis.outline());
    // Diagnostics in pipeline order: parse, then analyze-phase lossiness, then render-phase macros.
    var merged =
        mergeDiagnostics(
            mergeDiagnostics(diagnostics, analysis.diagnostics()), rendered.diagnostics());
    return new MarkdownResult(rendered.body(), analysis.metadata(), merged, rendered.unresolved());
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
