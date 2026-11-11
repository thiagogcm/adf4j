package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.options.ConversionOptions;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// The analyze phase: one {@link AdfNodeWalker} pass drives the heading and content-metadata
/// collectors together into a {@link DocumentAnalysis}. Sits between parsing and rendering.
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

  /// As {@link #analyze(AdfDocument, ConversionOptions, AttachmentInventory)}; without
  /// `lossiness` the diagnostics are left empty, for a render that reports what it visits itself.
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
