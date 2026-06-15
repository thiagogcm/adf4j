package dev.nthings.adf4j.confluence;

import dev.nthings.adf4j.ast.Attributes;
import org.jspecify.annotations.Nullable;

/**
 * Confluence-specific link/card metadata. This lives in the Confluence layer and is derived from
 * the product-neutral {@link Attributes} carried by AST nodes via {@link #from(Attributes)} — the
 * AST itself never depends on this type.
 */
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

  /** Reads the {@value #METADATA_KEY} extras from a node's attributes, or {@link #empty()}. */
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
