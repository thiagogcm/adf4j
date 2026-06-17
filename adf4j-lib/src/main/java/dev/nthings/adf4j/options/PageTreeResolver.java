package dev.nthings.adf4j.options;

import dev.nthings.adf4j.metadata.PageTreeReference;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Expands a Confluence `pagetree` or `children` macro into its descendant pages. The ADF
/// holds only the macro reference (Confluence renders these lists server-side from the space
/// hierarchy), so the caller supplies the pages; use {@link PageTreeReference#macro()} to tell the
/// two macros apart.
///
/// Return the pages as a flat, depth-tagged {@link PageTreeEntry} list (rendered as an indented
/// Markdown bullet list). A non-null result is authoritative: an empty list means "this page has no
/// descendants" and renders as nothing. Return `null` to decline (a thrown `RuntimeException` is
/// logged and also declines); only that falls back to the `{{pagetree}}`/`{{children}}` placeholder
/// token.
@FunctionalInterface
public interface PageTreeResolver {
  @Nullable List<PageTreeEntry> resolve(PageTreeReference reference);
}
