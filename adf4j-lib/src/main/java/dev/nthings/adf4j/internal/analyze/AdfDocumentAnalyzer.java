package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Analyze phase: one {@link AdfNodeWalker} pass feeds the heading and metadata collectors.
/// Stateless and thread-safe.
public final class AdfDocumentAnalyzer {

  private AdfDocumentAnalyzer() {}

  public static AdfDocumentAnalyzer createDefault() {
    return new AdfDocumentAnalyzer();
  }

  /// Reference and outline facts, without Markdown lossiness diagnostics.
  public DocumentFacts facts(
      AdfDocument document, UnknownNodePolicy unknownNodePolicy, AttachmentInventory attachments) {
    return collectFacts(document, unknownNodePolicy, attachments, null);
  }

  /// Facts plus the diagnostics that project Markdown's lossiness.
  public DocumentAnalysis analyze(
      AdfDocument document, UnknownNodePolicy unknownNodePolicy, AttachmentInventory attachments) {
    var lossinessCollector = new AdfLossinessCollector();
    var facts = collectFacts(document, unknownNodePolicy, attachments, lossinessCollector);
    return new DocumentAnalysis(facts, lossinessCollector.build(unknownNodePolicy));
  }

  private static DocumentFacts collectFacts(
      AdfDocument document,
      UnknownNodePolicy unknownNodePolicy,
      AttachmentInventory attachments,
      @Nullable AdfLossinessCollector lossinessCollector) {
    var headingCollector = new AdfHeadingCollector(unknownNodePolicy);
    var metadataExtractor = new AdfContentMetadataExtractor(attachments);
    AdfNodeWalker.walk(
        document,
        lossinessCollector == null
            ? List.of(headingCollector, metadataExtractor)
            : List.of(headingCollector, metadataExtractor, lossinessCollector));

    var outline = headingCollector.build();
    return new DocumentFacts(outline, metadataExtractor.build(outline.headings()));
  }
}
