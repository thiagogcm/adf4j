package dev.nthings.adf4j.options;

import dev.nthings.adf4j.metadata.PageTreeReference;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Expands a page-tree or children macro into a flat, depth-tagged list. An empty list renders
/// nothing. Null, or an exception (which emits a diagnostic), leaves a placeholder.
@FunctionalInterface
public interface PageTreeResolver {
  @Nullable List<PageTreeEntry> resolve(PageTreeReference reference);
}
