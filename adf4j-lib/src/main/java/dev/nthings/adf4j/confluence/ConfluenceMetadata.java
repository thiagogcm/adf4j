package dev.nthings.adf4j.confluence;

import dev.nthings.adf4j.ast.Attributes;
import org.jspecify.annotations.Nullable;

/// Confluence-specific link/card metadata. This lives in the Confluence layer and is derived from
/// the product-neutral {@link Attributes} carried by AST nodes via {@link #from(Attributes)}. The
/// AST itself never depends on this type.
///
/// `linkType` classifies the link as Confluence stored it (e.g. `page`), marking page links even
/// when no id is present. `pageId`, `contentId`, and `id` are candidate page-content ids in
/// preference order; the first non-blank of these is the metadata-derived page id, though an id
/// inferred from the link URL takes precedence over it. All four are `null` when the attributes
/// carried no Confluence metadata.
public record ConfluenceMetadata(
    @Nullable String linkType,
    @Nullable String pageId,
    @Nullable String contentId,
    @Nullable String id) {

  private static final ConfluenceMetadata EMPTY = new ConfluenceMetadata(null, null, null, null);
  private static final String METADATA_KEY = "__confluenceMetadata";

  public static ConfluenceMetadata empty() {
    return EMPTY;
  }

  /// Reads the `__confluenceMetadata` extras from a node's attributes, or {@link #empty()}.
  public static ConfluenceMetadata from(Attributes attrs) {
    if (attrs == null) {
      return EMPTY;
    }
    var metadata = attrs.object(METADATA_KEY);
    if (metadata.isEmpty()) {
      return EMPTY;
    }
    return new ConfluenceMetadata(
        metadata.string("linkType"),
        metadata.string("pageId"),
        metadata.string("contentId"),
        metadata.string("id"));
  }
}
