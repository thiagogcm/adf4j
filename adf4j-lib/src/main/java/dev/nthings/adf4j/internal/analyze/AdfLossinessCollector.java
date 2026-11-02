package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfBlock;
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
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Counts the unmodelled constructs the {@link AdfNodeWalker} visits so the conversion can report
/// whether rendering them was lossy: unknown node types (governed by the active
/// {@link UnknownNodePolicy}) and unknown marks (always dropped: the AST has no way to render a
/// mark type it does not model). Holds the counts for one document; create a fresh instance per
/// document.
final class AdfLossinessCollector implements NodeVisitor {

  private int unknownNodes;
  private int unknownMarks;

  @Override
  public void visitBlock(AdfBlock block) {
    switch (block) {
      case UnknownBlock _ -> unknownNodes++;
      case Paragraph _, Heading _, MediaSingle _, Media _ -> countUnknownMarks(block.marks());
      default -> {
        // Other blocks keep their marks in the AST, but rendering ignores them by design.
      }
    }
  }

  @Override
  public void visitInline(AdfInline inline) {
    switch (inline) {
      case UnknownInline _ -> unknownNodes++;
      case Text _, MediaInline _ -> countUnknownMarks(inline.marks());
      default -> {
        // Other inlines keep their marks in the AST, but rendering ignores them by design.
      }
    }
  }

  private void countUnknownMarks(List<AdfMark> marks) {
    for (var mark : marks) {
      if (mark instanceof UnknownMark) {
        unknownMarks++;
      }
    }
  }

  /// Diagnostics describing how the unknown constructs fared (empty when none). The unknown-node
  /// count is an upper bound: it counts every parsed unknown node, including any the renderer
  /// would also have dropped because it sits in a subtree dropped by design.
  List<Diagnostic> build(UnknownNodePolicy policy) {
    var issues = new ArrayList<Diagnostic>(2);
    if (unknownNodes > 0) {
      var nodeIssue = unknownNodeIssue(policy);
      if (nodeIssue != null) {
        issues.add(nodeIssue);
      }
    }
    if (unknownMarks > 0) {
      issues.add(
          new Diagnostic(
              Diagnostic.Code.UNKNOWN_MARK_DROPPED,
              unknownMarks + " unsupported mark(s) dropped from the output."));
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
