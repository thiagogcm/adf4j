package dev.nthings.adf4j.options;

import dev.nthings.adf4j.metadata.AttachmentReference;
import org.jspecify.annotations.Nullable;

/// Resolves an attachment macro's {@link AttachmentReference} to a URL or path. Null or blank, or a
/// thrown exception (which emits a diagnostic), falls back to the synthetic
/// `attachment:<fileId>` destination.
@FunctionalInterface
public interface AttachmentResolver {
  @Nullable String resolve(AttachmentReference reference);
}
