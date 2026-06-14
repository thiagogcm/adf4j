package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/**
 * The attributes shared by the three smart-card nodes (inline/block/embed): the card's {@code url}
 * (absent for datasource-backed cards), the optional {@code datasourceId}/{@code localId}/
 * {@code title}, and the remaining raw {@code attrs} (e.g. Confluence link metadata).
 */
public record CardAttrs(
    @Nullable String url,
    @Nullable String datasourceId,
    @Nullable String localId,
    @Nullable String title,
    Attributes attrs) {

  public CardAttrs {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }
}
