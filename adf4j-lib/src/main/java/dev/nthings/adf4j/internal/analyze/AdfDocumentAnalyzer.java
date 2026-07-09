package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.MarkdownOptions;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// The analyze phase: one {@link AdfNodeWalker} pass drives the heading and content-metadata
/// collectors together into a {@link DocumentAnalysis}. Sits between parsing and rendering.
///
/// Thread-safe. The walk is memoized for the last (document, Confluence context) identity pair,
/// so re-rendering one parsed document under varying resolvers pays for the tree walk once; only
/// the policy-dependent lossiness diagnostics are re-derived per call.
public final class AdfDocumentAnalyzer {

  private volatile @Nullable MemoizedWalk lastWalk;

  private AdfDocumentAnalyzer() {}

  public static AdfDocumentAnalyzer createDefault() {
    return new AdfDocumentAnalyzer();
  }

  public DocumentAnalysis analyze(@Nullable AdfDocument document, MarkdownOptions options) {
    if (document == null) {
      return DocumentAnalysis.empty();
    }

    var confluenceContext = options.confluenceContext();
    var walk = lastWalk;
    if (walk == null || walk.document != document || walk.confluenceContext != confluenceContext) {
      // A concurrent race just publishes one of two equivalent immutable results.
      walk = MemoizedWalk.of(document, confluenceContext);
      lastWalk = walk;
    }
    return new DocumentAnalysis(
        walk.outline, walk.metadata, walk.lossiness.build(options.unknownNodePolicy()));
  }

  // One walk's outcome, keyed by the identity of its inputs; immutable after the walk.
  private static final class MemoizedWalk {

    final AdfDocument document;
    final ConfluenceRenderContext confluenceContext;
    final HeadingOutline outline;
    final ContentMetadata metadata;
    final AdfLossinessCollector lossiness;

    private MemoizedWalk(
        AdfDocument document,
        ConfluenceRenderContext confluenceContext,
        HeadingOutline outline,
        ContentMetadata metadata,
        AdfLossinessCollector lossiness) {
      this.document = document;
      this.confluenceContext = confluenceContext;
      this.outline = outline;
      this.metadata = metadata;
      this.lossiness = lossiness;
    }

    static MemoizedWalk of(AdfDocument document, ConfluenceRenderContext confluenceContext) {
      var headingCollector = new AdfHeadingCollector();
      var metadataExtractor = new AdfContentMetadataExtractor(confluenceContext);
      var lossinessCollector = new AdfLossinessCollector();
      AdfNodeWalker.walk(
          document, List.of(headingCollector, metadataExtractor, lossinessCollector));

      var outline = headingCollector.build();
      var metadata = metadataExtractor.build(outline.headings());
      return new MemoizedWalk(document, confluenceContext, outline, metadata, lossinessCollector);
    }
  }
}
