package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.ast.UnknownBlock;
import dev.nthings.adf4j.ast.UnknownInline;
import dev.nthings.adf4j.ast.UnknownMark;
import dev.nthings.adf4j.internal.SourceIndex;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/// Counts the unmodelled constructs the {@link AdfNodeWalker} visits so the conversion can report
/// whether rendering them was lossy: unknown node types (governed by the active
/// {@link UnknownNodePolicy}) and unknown marks (always dropped: the AST has no way to render a
/// mark type it does not model). Holds the counts for one document; create a fresh instance per
/// document.
public final class AdfLossinessCollector implements NodeVisitor {

  private final SourceIndex source;
  private final List<String> nodePaths = new ArrayList<>();
  private final List<String> markPaths = new ArrayList<>();

  public AdfLossinessCollector(AdfDocument document) {
    source = new SourceIndex(document);
  }

  private int unknownNodes;
  private int unknownMarks;

  @Override
  public void visitBlock(AdfBlock block) {
    switch (block) {
      case UnknownBlock _ -> {
        unknownNodes++;
        nodePaths.add(source.path(block));
      }
      case Paragraph _, Heading _, MediaSingle _, Media _ ->
          countUnknownMarks(block.marks(), source.path(block));
      default -> {
        // Other blocks keep their marks in the AST, but rendering ignores them by design.
      }
    }
  }

  @Override
  public void visitInline(AdfInline inline) {
    switch (inline) {
      case UnknownInline _ -> {
        unknownNodes++;
        nodePaths.add(source.path(inline));
      }
      case Text _, MediaInline _ -> countUnknownMarks(inline.marks(), source.path(inline));
      default -> {
        // Other inlines keep their marks in the AST, but rendering ignores them by design.
      }
    }
  }

  private void countUnknownMarks(List<AdfMark> marks, String path) {
    for (int i = 0; i < marks.size(); i++) {
      var mark = marks.get(i);
      if (mark instanceof UnknownMark) {
        unknownMarks++;
        markPaths.add(path + "/marks/" + i);
      }
    }
  }

  /// Diagnostics describing how the unknown constructs fared (empty when none). The unknown-node
  /// count is an upper bound: it counts every parsed unknown node, including any the renderer
  /// would also have dropped because it sits in a subtree dropped by design.
  public List<Diagnostic> build(UnknownNodePolicy policy) {
    var issues = new ArrayList<Diagnostic>(2);
    if (unknownNodes > 0) {
      var nodeIssue = unknownNodeIssue(policy);
      if (nodeIssue != null) {
        issues.add(
            new Diagnostic(
                nodeIssue.code(),
                nodeIssue.message(),
                null,
                nodePaths,
                Map.of("count", String.valueOf(unknownNodes))));
      }
    }
    if (unknownMarks > 0) {
      issues.add(
          new Diagnostic(
              Diagnostic.Code.UNKNOWN_MARK_DROPPED,
              unknownMarks + " unsupported mark(s) dropped from the output.",
              null,
              markPaths,
              Map.of("count", String.valueOf(unknownMarks))));
    }
    return List.copyOf(issues);
  }

  private @Nullable Diagnostic unknownNodeIssue(UnknownNodePolicy policy) {
    return switch (policy) {
      case PLACEHOLDER ->
          new Diagnostic(
              Diagnostic.Code.UNKNOWN_NODE_PLACEHOLDER,
              unknownNodes
                  + " unsupported node(s) rendered as placeholders; original content not represented.");
      case SKIP ->
          new Diagnostic(
              Diagnostic.Code.UNKNOWN_NODE_SKIPPED,
              unknownNodes + " unsupported node(s) dropped from the output.");
      case PRESERVE_RAW ->
          new Diagnostic(
              Diagnostic.Code.UNKNOWN_NODE_PRESERVED,
              unknownNodes + " unsupported node(s) preserved as raw JSON.");
      // FAIL aborts the render with an exception, so there is no result to diagnose.
      case FAIL -> null;
    };
  }
}
