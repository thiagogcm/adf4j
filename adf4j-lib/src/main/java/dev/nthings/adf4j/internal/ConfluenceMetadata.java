package dev.nthings.adf4j.internal;

import dev.nthings.adf4j.ast.Attributes;
import org.jspecify.annotations.Nullable;

/// Confluence link/card metadata read from the product-neutral {@link Attributes}. The AST never
/// depends on this type. `pageId`, `contentId` and `id` are candidate page ids in that preference
/// order; all three are `null` when the attributes carry no Confluence metadata.
public record ConfluenceMetadata(
    @Nullable String pageId, @Nullable String contentId, @Nullable String id) {

  private static final ConfluenceMetadata EMPTY = new ConfluenceMetadata(null, null, null);
  private static final String METADATA_KEY = "__confluenceMetadata";

  public static ConfluenceMetadata empty() {
    return EMPTY;
  }

  public static ConfluenceMetadata from(Attributes attrs) {
    var metadata = attrs.object(METADATA_KEY);
    if (metadata.isEmpty()) {
      return EMPTY;
    }
    return new ConfluenceMetadata(
        metadata.string("pageId"), metadata.string("contentId"), metadata.string("id"));
  }
}
