package dev.nthings.adf4j.internal.engine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.ParseResult;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.analyze.AdfDocumentAnalyzer;
import dev.nthings.adf4j.internal.parser.AdfParsingService;
import dev.nthings.adf4j.internal.render.AdfRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles and runs the {@code parse → analyze → render} pipeline behind
 * {@link dev.nthings.adf4j.AdfToMarkdown}. Stateless and thread-safe once built; each phase owns its
 * own construction via {@code createDefault()}.
 */
public final class AdfPipeline {

  private static final Logger log = LoggerFactory.getLogger(AdfPipeline.class);

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

  public ParseResult parse(@Nullable String adfJson) {
    return parsingService.parse(adfJson);
  }

  public ContentMetadata analyze(@Nullable String adfJson, MarkdownOptions options) {
    if (adfJson == null || adfJson.isBlank()) {
      return ContentMetadata.empty();
    }
    var parsed = parsingService.parse(adfJson);
    if (parsed.document() == null || !parsed.validAdfRoot()) {
      return ContentMetadata.empty();
    }
    return analyze(parsed.document(), options);
  }

  public ContentMetadata analyze(@Nullable AdfDocument document, MarkdownOptions options) {
    if (document == null) {
      return ContentMetadata.empty();
    }
    return analyzer.analyze(document, options).metadata();
  }

  public MarkdownResult convert(@Nullable String adfJson, MarkdownOptions options) {
    if (adfJson == null || adfJson.isBlank()) {
      return render(null, options, List.of());
    }
    var parsed = parsingService.parse(adfJson);
    if (parsed.document() == null || !parsed.validAdfRoot()) {
      log.warn("Input is not a valid ADF document – returning empty result with diagnostics");
      return render(null, options, parsed.issues());
    }
    return render(parsed.document(), options, parsed.issues());
  }

  public MarkdownResult convert(@Nullable ParseResult parsed, MarkdownOptions options) {
    if (parsed == null) {
      return render(null, options, List.of());
    }
    return render(parsed.validAdfRoot() ? parsed.document() : null, options, parsed.issues());
  }

  public MarkdownResult convert(@Nullable AdfDocument document, MarkdownOptions options) {
    return render(document, options, List.of());
  }

  // A null document yields an empty (possibly titled) body.
  private MarkdownResult render(
      @Nullable AdfDocument document, MarkdownOptions options, List<Diagnostic> diagnostics) {
    var analysis = analyzer.analyze(document, options);
    var rendered = renderer.render(document, options, analysis.outline());
    // Diagnostics in pipeline order: parse, then analyze-phase lossiness, then render-phase macros.
    var merged = mergeDiagnostics(
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
