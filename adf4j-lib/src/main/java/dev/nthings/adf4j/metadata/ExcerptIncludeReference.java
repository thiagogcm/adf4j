package dev.nthings.adf4j.metadata;

import java.util.Map;

/**
 * One Confluence {@code excerpt-include} macro occurrence in a document — both a metadata fact (the
 * document embeds content owned by another page, so it depends on that page) and the request an
 * {@code ExcerptResolver} is asked to expand. {@code page} is the source page as the macro stores it:
 * its <em>title</em> (optionally prefixed {@code SPACEKEY:} for a cross-space include), never a page
 * node id — which is why these references live here and not in {@code ContentMetadata.pageRefs()}.
 * {@code excerptName} is the named-excerpt selector ({@code name} parameter), or {@code null} when
 * the macro targets the page's unnamed excerpt. {@code parameters} is the macro's full parameter map
 * (e.g. {@code nopanel}).
 */
public record ExcerptIncludeReference(
    String page, String excerptName, Map<String, String> parameters) {

  public ExcerptIncludeReference {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
  }
}
