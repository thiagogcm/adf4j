package dev.nthings.adf4j.internal;

import dev.nthings.adf4j.ast.AdfNode;
import java.util.ArrayList;
import java.util.List;

/// A node with its JSON Pointer in the source document. Pointers follow position, never object
/// identity, so one node instance reused in several places gets each place's own pointer.
public record Located<T extends AdfNode>(T node, String path) {

  /// The children of `parent`, which sits at `path`, that are a `type`, each with its
  /// `/content/<index>` pointer. Typed views such as `items()` filter `content`, so the index comes
  /// from `content` itself.
  public static <T extends AdfNode> List<Located<T>> children(
      AdfNode parent, String path, Class<T> type) {
    var content = parent.content();
    var children = new ArrayList<Located<T>>(content.size());
    for (int i = 0; i < content.size(); i++) {
      if (type.isInstance(content.get(i))) {
        children.add(new Located<>(type.cast(content.get(i)), path + "/content/" + i));
      }
    }
    return children;
  }

  /// The pointer of the mark at `index` on the node at `path`.
  public static String mark(String path, int index) {
    return path + "/marks/" + index;
  }
}
