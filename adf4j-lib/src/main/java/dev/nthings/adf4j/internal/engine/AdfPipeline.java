package dev.nthings.adf4j.internal.engine;

import java.util.List;

import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.ParseIssue;
import dev.nthings.adf4j.result.ParseResult;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.parser.AdfAstParser;
import dev.nthings.adf4j.internal.render.AdfContentMetadataExtractor;
import dev.nthings.adf4j.internal.render.AdfHeadingCollector;
import dev.nthings.adf4j.internal.render.AdfRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.json.JsonMapper;

/**
 * Assembles and runs the {@code parse → analyze → render} pipeline behind
 * {@link dev.nthings.adf4j.AdfToMarkdown}. Stateless and thread-safe once built.
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
    var mapper = JsonMapper.builder().build();
    var parsingService = new AdfParsingService(mapper, new AdfAstParser(mapper));
    var analyzer =
        new AdfDocumentAnalyzer(new AdfHeadingCollector(), new AdfContentMetadataExtractor());
    return new AdfPipeline(parsingService, analyzer, AdfRenderer.createDefault());
  }

  public ParseResult parse(String adfJson) {
    return parsingService.parse(adfJson);
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
    return new MarkdownResult(body, analysis.metadata(), diagnostics);
  }
}
