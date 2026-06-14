package dev.nthings.adf4j.options;

import org.jspecify.annotations.Nullable;

/**
 * One descendant page in an expanded {@code pagetree} or {@code children} macro, supplied by a
 * {@link PageTreeResolver}. The entries are rendered as an indented Markdown bullet list in iteration
 * order.
 *
 * <p>{@code depth} is the 0-based nesting level; the renderer shifts the shallowest entry to column 0,
 * so a list starting at depth {@code 1} renders the same as one starting at {@code 0}. {@code title}
 * is the visible label (Markdown-escaped). {@code pageNodeId} is routed through the conversion's
 * {@code pageLinkResolver} — the same hook that rewrites inline page links — to produce the link
 * destination; an entry whose id is {@code null} or does not resolve renders as plain text.
 */
public record PageTreeEntry(int depth, String title, @Nullable String pageNodeId) {

  public PageTreeEntry {
    depth = Math.max(0, depth);
  }
}
