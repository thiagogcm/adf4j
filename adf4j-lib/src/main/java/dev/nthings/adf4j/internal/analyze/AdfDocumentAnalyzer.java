package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.ast.AdfDocument;

/**
 * The analyze phase: a single logical pass that collects the heading outline and extracts content
 * metadata, sitting between parsing and rendering.
 */
public final class AdfDocumentAnalyzer {

  private final AdfHeadingCollector headingCollector;
  private final AdfContentMetadataExtractor metadataExtractor;

  AdfDocumentAnalyzer(
      AdfHeadingCollector headingCollector, AdfContentMetadataExtractor metadataExtractor) {
    this.headingCollector = headingCollector;
    this.metadataExtractor = metadataExtractor;
  }

  public static AdfDocumentAnalyzer createDefault() {
    return new AdfDocumentAnalyzer(new AdfHeadingCollector(), new AdfContentMetadataExtractor());
  }

  public DocumentAnalysis analyze(AdfDocument document, MarkdownOptions options) {
    if (document == null) {
      return DocumentAnalysis.empty();
    }
    var outline = headingCollector.collect(document);
    var metadata = metadataExtractor.extract(document, options, outline.headings());
    return new DocumentAnalysis(outline, metadata);
  }
}
