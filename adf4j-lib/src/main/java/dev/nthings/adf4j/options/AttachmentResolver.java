package dev.nthings.adf4j.options;

import org.jspecify.annotations.Nullable;

import dev.nthings.adf4j.metadata.AttachmentReference;

/**
 * Resolves a Confluence {@code attachment:} reference (a "view file"/{@code viewpdf} macro that the
 * library has already resolved to a {@link AttachmentReference} with a {@code fileId}) to a concrete
 * URL or path. Returning {@code null} or blank falls back to the synthetic
 * {@code attachment:<fileId>} destination. Symmetric to {@link MediaResolver}, but keyed off the
 * resolved attachment reference rather than a raw media node, so consumers can localize attachment
 * macros without string-surgery on the rendered Markdown.
 */
@FunctionalInterface
public interface AttachmentResolver {
  @Nullable String resolve(AttachmentReference reference);
}
