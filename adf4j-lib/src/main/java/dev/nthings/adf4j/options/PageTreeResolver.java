package dev.nthings.adf4j.options;

import dev.nthings.adf4j.metadata.PageTreeReference;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Expands a Confluence page-tree macro into a flat, depth-tagged list of pages.
/// An empty list is authoritative and renders nothing. Null leaves a placeholder.
/// Exceptions emit a diagnostic and use the same fallback.
@FunctionalInterface
public interface PageTreeResolver {
  @Nullable List<PageTreeEntry> resolve(PageTreeReference reference);
}
