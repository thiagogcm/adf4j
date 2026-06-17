package dev.nthings.adf4j.metadata;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/// One Confluence `excerpt-include` macro occurrence in a document, both a metadata fact (the
/// document embeds content owned by another page, so it depends on that page) and the request an
/// `ExcerptResolver` is asked to expand. `page` is the source page as the macro stores it:
/// its *title* (optionally prefixed `SPACEKEY:` for a cross-space include), never a page
/// node id. That is why these references live here and not in `ContentMetadata.pageRefs()`.
/// `excerptName` is the named-excerpt selector (`name` parameter), or `null` when
/// the macro targets the page's unnamed excerpt. `parameters` is the macro's full parameter map
/// (e.g. `nopanel`).
public record ExcerptIncludeReference(
    String page, @Nullable String excerptName, Map<String, String> parameters) {

  public ExcerptIncludeReference {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
  }
}
