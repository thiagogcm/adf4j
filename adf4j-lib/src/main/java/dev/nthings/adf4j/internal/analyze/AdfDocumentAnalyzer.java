package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.options.ConversionOptions;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Analyze phase: one {@link AdfNodeWalker} pass feeds the heading and metadata collectors.
/// Stateless and thread-safe.
public final class AdfDocumentAnalyzer {

  private AdfDocumentAnalyzer() {}

  public static AdfDocumentAnalyzer createDefault() {
    return new AdfDocumentAnalyzer();
  }

  public DocumentAnalysis analyze(
      @Nullable AdfDocument document, ConversionOptions options, AttachmentInventory attachments) {
    return analyze(document, options, attachments, true);
  }

  /// Without `lossiness`, diagnostics stay empty: the render reports what it visits itself.
  public DocumentAnalysis analyze(
      @Nullable AdfDocument document,
      ConversionOptions options,
      AttachmentInventory attachments,
      boolean lossiness) {
    if (document == null) {
      return DocumentAnalysis.empty();
    }

    var headingCollector = new AdfHeadingCollector(options.unknownNodePolicy());
    var metadataExtractor = new AdfContentMetadataExtractor(attachments);
    var lossinessCollector = lossiness ? new AdfLossinessCollector() : null;
    AdfNodeWalker.walk(
        document,
        lossinessCollector == null
            ? List.of(headingCollector, metadataExtractor)
            : List.of(headingCollector, metadataExtractor, lossinessCollector));

    var outline = headingCollector.build();
    return new DocumentAnalysis(
        outline,
        metadataExtractor.build(outline.headings()),
        lossinessCollector == null
            ? List.of()
            : lossinessCollector.build(options.unknownNodePolicy()));
  }
}
