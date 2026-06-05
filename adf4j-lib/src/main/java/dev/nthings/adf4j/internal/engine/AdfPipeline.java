package dev.nthings.adf4j.internal.engine;

import java.util.ArrayList;
import java.util.List;

import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.ParseIssue;
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

  public ParseResult parse(String adfJson) {
    return parsingService.parse(adfJson);
  }

  public ContentMetadata analyze(String adfJson, MarkdownOptions options) {
    if (adfJson == null || adfJson.isBlank()) {
      return ContentMetadata.empty();
    }
    var parsed = parsingService.parse(adfJson);
    if (parsed.document() == null || !parsed.validAdfRoot()) {
      return ContentMetadata.empty();
    }
    return analyze(parsed.document(), options);
  }

  public ContentMetadata analyze(AdfDocument document, MarkdownOptions options) {
    if (document == null) {
      return ContentMetadata.empty();
    }
    return analyzer.analyze(document, options).metadata();
  }

  public MarkdownResult convert(String adfJson, MarkdownOptions options) {
    if (adfJson == null || adfJson.isBlank()) {
      return MarkdownResult.empty();
    }
    var parsed = parsingService.parse(adfJson);
    if (parsed.document() == null || !parsed.validAdfRoot()) {
      log.warn("Input is not a valid ADF document – returning empty result with diagnostics");
      return new MarkdownResult("", ContentMetadata.empty(), parsed.issues());
    }
    return render(parsed.document(), options, parsed.issues());
  }

  public MarkdownResult convert(AdfDocument document, MarkdownOptions options) {
    return render(document, options, List.of());
  }

  private MarkdownResult render(
      AdfDocument document, MarkdownOptions options, List<ParseIssue> diagnostics) {
    if (document == null) {
      return new MarkdownResult("", ContentMetadata.empty(), diagnostics);
    }
    var analysis = analyzer.analyze(document, options);
    var body = renderer.render(document, options, analysis.outline());
    return new MarkdownResult(body, analysis.metadata(), mergeDiagnostics(diagnostics, analysis.diagnostics()));
  }

  // Parse-phase diagnostics first, then the analyze-phase lossiness diagnostics, preserving order.
  private static List<ParseIssue> mergeDiagnostics(
      List<ParseIssue> parseDiagnostics, List<ParseIssue> analysisDiagnostics) {
    if (analysisDiagnostics.isEmpty()) {
      return parseDiagnostics;
    }
    var merged = new ArrayList<ParseIssue>(parseDiagnostics.size() + analysisDiagnostics.size());
    merged.addAll(parseDiagnostics);
    merged.addAll(analysisDiagnostics);
    return merged;
  }
}
