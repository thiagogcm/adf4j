package dev.nthings.adf4j.internal.engine;

import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.render.AdfContentMetadataExtractor;
import dev.nthings.adf4j.internal.render.AdfHeadingCollector;

/**
 * The analyze phase: a single logical pass that collects the heading outline and extracts content
 * metadata, sitting between parsing and rendering.
 */
final class AdfDocumentAnalyzer {

  private final AdfHeadingCollector headingCollector;
  private final AdfContentMetadataExtractor metadataExtractor;

  AdfDocumentAnalyzer(
      AdfHeadingCollector headingCollector, AdfContentMetadataExtractor metadataExtractor) {
    this.headingCollector = headingCollector;
    this.metadataExtractor = metadataExtractor;
  }

  DocumentAnalysis analyze(AdfDocument document, MarkdownOptions options) {
    if (document == null) {
      return DocumentAnalysis.empty();
    }
    var outline = headingCollector.collect(document);
    var metadata = metadataExtractor.extract(document, options, outline.headings());
    return new DocumentAnalysis(outline, metadata);
  }
}
