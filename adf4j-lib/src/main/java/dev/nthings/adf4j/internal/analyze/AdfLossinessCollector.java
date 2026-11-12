package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.ast.AdfUnknown;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.ast.UnknownMark;
import dev.nthings.adf4j.internal.Located;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.ArrayList;
import java.util.List;

/// Records paths of unmodelled nodes and marks so analysis can report lossiness. Unknown nodes
/// follow the active {@link UnknownNodePolicy}; unknown marks are always dropped. One instance per
/// document. Rendering reports the same diagnostics through {@link #diagnostics}.
public final class AdfLossinessCollector implements NodeVisitor {

  private final List<String> nodePaths = new ArrayList<>();
  private final List<String> markPaths = new ArrayList<>();

  /// Whether rendering reads `node`'s marks; marks on other nodes are ignored by design.
  public static boolean rendersMarks(AdfNode node) {
    return switch (node) {
      case Paragraph _, Heading _, MediaSingle _, Media _, Text _, MediaInline _ -> true;
      default -> false;
    };
  }

  @Override
  public void visitBlock(AdfBlock block, String path) {
    visit(block, path);
  }

  @Override
  public void visitInline(AdfInline inline, String path) {
    visit(inline, path);
  }

  private void visit(AdfNode node, String path) {
    if (node instanceof AdfUnknown) {
      nodePaths.add(path);
    } else if (rendersMarks(node)) {
      for (var i = 0; i < node.marks().size(); i++) {
        if (node.marks().get(i) instanceof UnknownMark) {
          markPaths.add(Located.mark(path, i));
        }
      }
    }
  }

  /// The unknown-node count is an upper bound: it includes nodes inside subtrees the renderer
  /// drops by design.
  public List<Diagnostic> build(UnknownNodePolicy policy) {
    return diagnostics(policy, nodePaths, markPaths);
  }

  /// Lossiness diagnostics for unknown nodes and marks at the given paths.
  public static List<Diagnostic> diagnostics(
      UnknownNodePolicy policy, List<String> nodePaths, List<String> markPaths) {
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
          // FAIL throws instead of rendering, so there is nothing to diagnose.
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
