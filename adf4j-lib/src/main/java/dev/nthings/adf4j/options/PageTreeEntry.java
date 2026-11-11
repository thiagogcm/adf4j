package dev.nthings.adf4j.options;

import org.jspecify.annotations.Nullable;

/// One descendant page in an expanded `pagetree` or `children` macro, returned by a
/// {@link PageTreeResolver}. Iteration order and depth describe the page hierarchy.
///
/// `depth` is 0-based. `title` is plain text for the renderer to escape, `null` meaning empty.
/// `pageNodeId` can be resolved into a destination through `PageLinkResolver`. Markdown renders
/// entries as an indented bullet list, with plain labels for absent or unresolved IDs.
public record PageTreeEntry(int depth, String title, @Nullable String pageNodeId) {

  public PageTreeEntry(int depth, @Nullable String title, @Nullable String pageNodeId) {
    this.depth = Math.max(0, depth);
    this.title = title == null ? "" : title;
    this.pageNodeId = pageNodeId;
  }
}
