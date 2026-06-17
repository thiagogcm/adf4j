package dev.nthings.adf4j.options;

import dev.nthings.adf4j.ast.MediaAttrs;
import org.jspecify.annotations.Nullable;

/// Resolves a file/attachment media node (which typically carries ids rather than a URL) to a
/// concrete URL. Returning `null` or blank declines (so does throwing, which is logged), keeping
/// the synthetic `media:<collection>/<id>` placeholder. Keyed off the raw {@link MediaAttrs}; for
/// the Confluence attachment macro use the reference-keyed {@link AttachmentResolver} instead. Lets
/// callers turn file media into real links via a base URL or a lookup.
@FunctionalInterface
public interface MediaResolver {
  @Nullable String resolve(MediaAttrs attrs);
}
