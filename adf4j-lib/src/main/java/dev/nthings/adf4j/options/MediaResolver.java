package dev.nthings.adf4j.options;

import org.jspecify.annotations.Nullable;

import dev.nthings.adf4j.ast.MediaAttrs;

/**
 * Resolves a file/attachment media node (which carries ids, not a URL) to a concrete URL. Returning
 * {@code null} or blank falls back to the synthetic {@code media:<collection>/<id>} placeholder.
 * Parallels the Confluence {@code attachment:} handling, letting callers turn file media into real
 * links via a base URL or a lookup.
 */
@FunctionalInterface
public interface MediaResolver {
  @Nullable String resolve(MediaAttrs attrs);
}
