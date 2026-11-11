package dev.nthings.adf4j.options;

import org.jspecify.annotations.Nullable;

/// Resolves a file ID for media and attachment macros. Specialized hooks take precedence.
@FunctionalInterface
public interface FileResolver {
  @Nullable String resolve(String fileId);
}
