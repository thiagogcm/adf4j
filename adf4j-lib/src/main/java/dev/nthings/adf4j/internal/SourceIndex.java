package dev.nthings.adf4j.internal;

import dev.nthings.adf4j.ast.AdfNode;
import java.util.IdentityHashMap;
import java.util.Map;

/// JSON pointers for nodes in a source document. Identity preserves equal sibling occurrences.
public final class SourceIndex {
  private final Map<Object, String> paths = new IdentityHashMap<>();

  public SourceIndex(AdfNode root) {
    index(root, "");
  }

  private void index(AdfNode node, String path) {
    paths.put(node, path);
    for (int i = 0; i < node.marks().size(); i++)
      paths.put(node.marks().get(i), path + "/marks/" + i);
    for (int i = 0; i < node.content().size(); i++)
      index(node.content().get(i), path + "/content/" + i);
  }

  public String path(Object node) {
    return paths.getOrDefault(node, "");
  }
}
