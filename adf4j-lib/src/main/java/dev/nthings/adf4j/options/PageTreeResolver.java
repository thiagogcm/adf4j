package dev.nthings.adf4j.options;

import java.util.List;

import dev.nthings.adf4j.metadata.PageTreeReference;

/**
 * Expands a Confluence {@code pagetree} or {@code children} macro into its descendant pages. The ADF
 * holds only the macro reference (Confluence renders these lists server-side from the space
 * hierarchy), so the caller supplies the pages; use {@link PageTreeReference#macro()} to tell the two
 * macros apart.
 *
 * <p>Return the pages as a flat, depth-tagged {@link PageTreeEntry} list (rendered as an indented
 * Markdown bullet list). A non-null result is authoritative: an empty list means "this page has no
 * descendants" and renders as nothing. Return {@code null} (or throw) to decline — only that falls
 * back to the {@code {{pagetree}}} / {@code {{children}}} placeholder token.
 */
@FunctionalInterface
public interface PageTreeResolver {
  List<PageTreeEntry> resolve(PageTreeReference reference);
}
