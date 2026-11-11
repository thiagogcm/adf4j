package dev.nthings.adf4j.options;

import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import org.jspecify.annotations.Nullable;

/// Expands a Confluence `excerpt-include` macro into Markdown. The ADF holds only the reference,
/// so the caller supplies the content, e.g. from the source page's `ContentMetadata.excerpts()`.
///
/// The string is emitted verbatim, unescaped: escape untrusted content yourself. An empty string
/// suppresses the macro's output. Return `null` to decline, or throw (which emits CALLBACK_FAILED);
/// either falls back to a placeholder and records the reference in `MarkdownResult.unresolved()`.
@FunctionalInterface
public interface ExcerptResolver {
  @Nullable String resolve(ExcerptIncludeReference reference);
}
