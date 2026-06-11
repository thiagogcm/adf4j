package dev.nthings.adf4j.internal.analyze;

import java.util.List;

import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.ast.AdfDocument;

/**
 * The analyze phase: one {@link AdfNodeWalker} pass drives the heading and content-metadata collectors
 * together into a {@link DocumentAnalysis}. Stateless and thread-safe (the per-document accumulation
 * lives in the fresh collectors), sitting between parsing and rendering.
 */
public final class AdfDocumentAnalyzer {

  private AdfDocumentAnalyzer() {
  }

  public static AdfDocumentAnalyzer createDefault() {
    return new AdfDocumentAnalyzer();
  }

  public DocumentAnalysis analyze(AdfDocument document, MarkdownOptions options) {
    if (document == null) {
      return DocumentAnalysis.empty();
    }

    var headingCollector = new AdfHeadingCollector();
    var metadataExtractor = new AdfContentMetadataExtractor(options.confluenceContext());
    var lossinessCollector = new AdfLossinessCollector();
    AdfNodeWalker.walk(
        document, List.of(headingCollector, metadataExtractor, lossinessCollector));

    var outline = headingCollector.build();
    var metadata = metadataExtractor.build(outline.headings());
    var diagnostics = lossinessCollector.build(options.unknownNodePolicy());
    return new DocumentAnalysis(outline, metadata, diagnostics);
  }
}
