package dev.nthings.adf4j.options;

import dev.nthings.adf4j.metadata.AttachmentReference;
import org.jspecify.annotations.Nullable;

/// Resolves a Confluence `attachment:` reference (a "view file"/`viewpdf` macro that the
/// library has already resolved to a {@link AttachmentReference} with a `fileId`) to a concrete
/// URL or path. Returning `null` or blank (or throwing, which is logged) falls back to the
/// synthetic `attachment:<fileId>` destination. Symmetric to {@link MediaResolver}, but keyed
/// off the resolved attachment reference rather than a raw media node, so consumers can localize
/// attachment macros without string-surgery on the rendered Markdown.
@FunctionalInterface
public interface AttachmentResolver {
  @Nullable String resolve(AttachmentReference reference);
}
