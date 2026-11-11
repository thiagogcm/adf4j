package dev.nthings.adf4j.metadata;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/// A Confluence `excerpt-include` macro occurrence: a metadata fact and the request an
/// `ExcerptResolver` expands. `page` is the source page's *title* (optionally `SPACEKEY:`
/// prefixed), not a page node ID, so these are not in `ContentMetadata.pageIds()`. `excerptName`
/// is null for the unnamed excerpt. `parameters` is the macro's full parameter map.
public record ExcerptIncludeReference(
    String page, @Nullable String excerptName, Map<String, String> parameters) {

  public ExcerptIncludeReference {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
  }
}
