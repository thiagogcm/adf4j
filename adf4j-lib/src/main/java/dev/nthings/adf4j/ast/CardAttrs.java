package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// The attributes shared by the three smart-card nodes ({@link InlineCard}/{@link BlockCard}/
/// {@link EmbedCard}): the card's `url` (absent for datasource-backed cards), the optional
/// `datasourceId`/`localId`/`title`, and the remaining raw `attrs` (e.g. Confluence link
/// metadata, which a `PageLinkResolver` reads to rewrite an internal page card's destination).
/// A read-only view derived from the node by {@link #from(Attributes)}.
public record CardAttrs(
    @Nullable String url,
    @Nullable String datasourceId,
    @Nullable String localId,
    @Nullable String title,
    Attributes attrs) {

  public CardAttrs {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }

  /// Reads a card node's fields. The url falls back to `data.url` and the title is `data.name`.
  public static CardAttrs from(Attributes attrs) {
    var data = attrs.object("data");
    var url = attrs.text("url");
    if (url == null || url.isBlank()) {
      url = data.text("url");
    }
    return new CardAttrs(
        url,
        attrs.object("datasource").text("id"),
        attrs.text("localId"),
        data.text("name"),
        attrs);
  }
}
