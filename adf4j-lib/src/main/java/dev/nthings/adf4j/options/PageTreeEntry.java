package dev.nthings.adf4j.options;

import org.jspecify.annotations.Nullable;

/// One descendant page in an expanded `pagetree` or `children` macro, returned by a
/// {@link PageTreeResolver}. Entries render as an indented bullet list in iteration order.
///
/// `depth` is 0-based; the shallowest entry renders at column 0. `title` is the Markdown-escaped
/// label, `null` meaning empty. `pageNodeId` is routed through `pageLinkResolver` for the link; a
/// null or unresolved ID renders plain text.
public record PageTreeEntry(int depth, String title, @Nullable String pageNodeId) {

  public PageTreeEntry(int depth, @Nullable String title, @Nullable String pageNodeId) {
    this.depth = Math.max(0, depth);
    this.title = title == null ? "" : title;
    this.pageNodeId = pageNodeId;
  }
}
