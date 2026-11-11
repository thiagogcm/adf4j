package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.ast.UnknownBlock;
import dev.nthings.adf4j.ast.UnknownInline;
import dev.nthings.adf4j.ast.UnknownMark;
import dev.nthings.adf4j.internal.Located;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.ArrayList;
import java.util.List;

/// Records where the unmodelled constructs it visits sit, so the conversion can report whether
/// rendering them was lossy: unknown node types (governed by the active {@link UnknownNodePolicy})
/// and unknown marks (always dropped: the AST has no way to render a mark type it does not model).
/// Holds one document's findings; create a fresh instance per document.
public final class AdfLossinessCollector implements NodeVisitor {

  private final List<String> nodePaths = new ArrayList<>();
  private final List<String> markPaths = new ArrayList<>();

  @Override
  public void visitBlock(AdfBlock block, String path) {
    switch (block) {
      case UnknownBlock _ -> nodePaths.add(path);
      case Paragraph _, Heading _, MediaSingle _, Media _ -> unknownMarks(block, path);
      default -> {
        // Other blocks keep their marks in the AST, but rendering ignores them by design.
      }
    }
  }

  @Override
  public void visitInline(AdfInline inline, String path) {
    switch (inline) {
      case UnknownInline _ -> nodePaths.add(path);
      case Text _, MediaInline _ -> unknownMarks(inline, path);
      default -> {
        // Other inlines keep their marks in the AST, but rendering ignores them by design.
      }
    }
  }

  private void unknownMarks(AdfNode node, String path) {
    for (int i = 0; i < node.marks().size(); i++) {
      if (node.marks().get(i) instanceof UnknownMark) {
        markPaths.add(Located.mark(path, i));
      }
    }
  }

  /// Diagnostics describing how the unknown constructs fared (empty when none). The unknown-node
  /// count is an upper bound: it counts every parsed unknown node, including any the renderer
  /// would also have dropped because it sits in a subtree dropped by design.
  public List<Diagnostic> build(UnknownNodePolicy policy) {
    var issues = new ArrayList<Diagnostic>(2);
    var nodes = nodePaths.size();
    var nodeIssue =
        switch (policy) {
          case PLACEHOLDER ->
              new Diagnostic(
                  Diagnostic.Code.UNKNOWN_NODE_PLACEHOLDER,
                  nodes
                      + " unsupported node(s) rendered as placeholders; original content not"
                      + " represented.");
          case SKIP ->
              new Diagnostic(
                  Diagnostic.Code.UNKNOWN_NODE_SKIPPED,
                  nodes + " unsupported node(s) dropped from the output.");
          case PRESERVE_RAW ->
              new Diagnostic(
                  Diagnostic.Code.UNKNOWN_NODE_PRESERVED,
                  nodes + " unsupported node(s) preserved as raw JSON.");
          // FAIL aborts the render with an exception, so there is no result to diagnose.
          case FAIL -> null;
        };
    if (nodes > 0 && nodeIssue != null) {
      issues.add(nodeIssue.at(nodePaths));
    }
    if (!markPaths.isEmpty()) {
      issues.add(
          new Diagnostic(
                  Diagnostic.Code.UNKNOWN_MARK_DROPPED,
                  markPaths.size() + " unsupported mark(s) dropped from the output.")
              .at(markPaths));
    }
    return List.copyOf(issues);
  }
}
