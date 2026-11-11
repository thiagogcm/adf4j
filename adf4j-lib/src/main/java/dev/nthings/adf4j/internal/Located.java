package dev.nthings.adf4j.internal;

import dev.nthings.adf4j.ast.AdfNode;
import java.util.ArrayList;
import java.util.List;

/// A node and its JSON Pointer in the source. Pointers follow position, not identity, so a node
/// reused in two places gets two pointers.
public record Located<T extends AdfNode>(T node, String path) {

  /// The `type` children of `parent` (at `path`), each with its `/content/<index>` pointer. The
  /// index is into `content`, not into a filtered typed view.
  public static <T extends AdfNode> List<Located<T>> children(
      AdfNode parent, String path, Class<T> type) {
    var content = parent.content();
    var children = new ArrayList<Located<T>>(content.size());
    for (var i = 0; i < content.size(); i++) {
      if (type.isInstance(content.get(i))) {
        children.add(new Located<>(type.cast(content.get(i)), path + "/content/" + i));
      }
    }
    return children;
  }

  public static String mark(String path, int index) {
    return path + "/marks/" + index;
  }
}
