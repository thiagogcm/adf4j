package dev.nthings.adf4j.options;

import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import org.jspecify.annotations.Nullable;

/// Expands a Confluence `excerpt-include` macro into the embedded content. The ADF holds only
/// the reference (Confluence composes the excerpt server-side from the source page), so the caller
/// supplies the Markdown; the source page's own conversion exposes its excerpt regions as
/// `ContentMetadata.excerpts()`, ready to render and index for this lookup.
///
/// Return the excerpt as Markdown, rendered in place of the macro. The returned string is emitted
/// verbatim and is not escaped/sanitized, so escape untrusted content yourself. An empty string is
/// a valid answer that suppresses the macro's output. Return `null` to decline (or throw; a thrown
/// `RuntimeException` is logged); only that falls back to the placeholder and
/// records the reference on `MarkdownResult.unresolved()`.
@FunctionalInterface
public interface ExcerptResolver {
  @Nullable String resolve(ExcerptIncludeReference reference);
}
