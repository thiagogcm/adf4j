package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// The attributes shared by the three smart-card nodes ({@link InlineCard}/{@link BlockCard}/
/// {@link EmbedCard}): the card's `url` (absent for datasource-backed cards), the optional
/// `datasourceId`/`localId`/`title`, and the remaining raw `attrs` (e.g. Confluence link
/// metadata, which a `PageLinkResolver` reads to rewrite an internal page card's destination).
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
