package dev.nthings.adf4j.options;

import dev.nthings.adf4j.ast.AdfMedia;
import org.jspecify.annotations.Nullable;

/// Resolves file media to a URL. Null or blank declines and uses the normal fallback.
/// Exceptions emit a diagnostic. The shared FileResolver runs after this hook declines.
@FunctionalInterface
public interface MediaResolver {
  @Nullable String resolve(AdfMedia media);
}
