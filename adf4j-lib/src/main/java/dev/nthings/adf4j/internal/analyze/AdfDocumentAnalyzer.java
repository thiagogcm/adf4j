package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.options.MarkdownOptions;
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
      @Nullable AdfDocument document, MarkdownOptions options, AttachmentInventory attachments) {
    if (document == null) {
      return DocumentAnalysis.empty();
    }

    var headingCollector = new AdfHeadingCollector(options.unknownNodePolicy());
    var metadataExtractor = new AdfContentMetadataExtractor(attachments);
    var lossinessCollector = new AdfLossinessCollector();
    AdfNodeWalker.walk(document, List.of(headingCollector, metadataExtractor, lossinessCollector));

    var outline = headingCollector.build();
    return new DocumentAnalysis(
        outline,
        metadataExtractor.build(outline.headings()),
        lossinessCollector.build(options.unknownNodePolicy()));
  }
}
