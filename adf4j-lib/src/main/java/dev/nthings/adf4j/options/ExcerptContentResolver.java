package dev.nthings.adf4j.options;

import dev.nthings.adf4j.metadata.ExcerptDefinition;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import org.jspecify.annotations.Nullable;

/// Resolves a Confluence `excerpt-include` macro into parsed ADF blocks, typically taken from the
/// source page's `ContentMetadata.excerpts()`.
///
/// Unlike {@link ExcerptResolver}, this hook is renderer-neutral: every renderer formats the
/// returned AST natively in its own output format. Returning an {@link ExcerptDefinition} with
/// empty content suppresses the macro; returning `null` or throwing (which emits `CALLBACK_FAILED`)
/// declines so the renderer falls back to {@link ExcerptResolver} or a placeholder.
@FunctionalInterface
public interface ExcerptContentResolver {
  @Nullable ExcerptDefinition resolve(ExcerptIncludeReference reference);
}
