package dev.nthings.adf4j.options;

import org.jspecify.annotations.Nullable;

/// One descendant page in an expanded `pagetree` or `children` macro, supplied by a
/// {@link PageTreeResolver}. The entries are rendered as an indented Markdown bullet list in
/// iteration order.
///
/// `depth` is the 0-based nesting level; the renderer shifts the shallowest entry to column 0,
/// so a list starting at depth `1` renders the same as one starting at `0`. `title`
/// is the visible label (Markdown-escaped). `pageNodeId` is routed through the conversion's
/// `pageLinkResolver` (the same hook that rewrites inline page links) to produce the link
/// destination; an entry whose id is `null` or does not resolve renders as plain text.
public record PageTreeEntry(int depth, String title, @Nullable String pageNodeId) {

  public PageTreeEntry {
    depth = Math.max(0, depth);
  }
}
