package dev.nthings.adf4j.options;

import org.jspecify.annotations.Nullable;

import dev.nthings.adf4j.metadata.ExcerptIncludeReference;

/**
 * Expands a Confluence {@code excerpt-include} macro into the embedded content. The ADF holds only
 * the reference (Confluence composes the excerpt server-side from the source page), so the caller
 * supplies the Markdown; the source page's own conversion exposes its excerpt regions as
 * {@code ContentMetadata.excerpts()}, ready to render and index for this lookup.
 *
 * <p>Return the excerpt as Markdown, rendered in place of the macro. The returned string is emitted
 * verbatim and is not escaped/sanitized — escape untrusted content yourself. An empty string is a
 * valid answer that suppresses the macro's output. Return {@code null} (or throw — a thrown
 * {@code RuntimeException} is logged) to decline; only that falls back to the placeholder and
 * records the reference on {@code MarkdownResult.unresolved()}.
 */
@FunctionalInterface
public interface ExcerptResolver {
  @Nullable String resolve(ExcerptIncludeReference reference);
}
