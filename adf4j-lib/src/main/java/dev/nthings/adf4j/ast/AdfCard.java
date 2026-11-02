package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// A smart-card node, {@link InlineCard}, {@link BlockCard} or {@link EmbedCard}, with its fields
/// read from `attrs`. The rest of `attrs` stays reachable, such as the Confluence link metadata a
/// `PageLinkResolver` uses to rewrite an internal page card's destination.
public sealed interface AdfCard extends AdfNode permits InlineCard, BlockCard, EmbedCard {

  /// The card's URL, falling back to `data.url`. Absent for datasource-backed cards.
  default @Nullable String url() {
    var url = attrs().text("url");
    return url == null || url.isBlank() ? attrs().object("data").text("url") : url;
  }

  /// The id of the datasource backing the card.
  default @Nullable String datasourceId() {
    return attrs().object("datasource").text("id");
  }

  /// The editor's local id for this node.
  default @Nullable String localId() {
    return attrs().text("localId");
  }

  /// The card's title, from `data.name`.
  default @Nullable String title() {
    return attrs().object("data").text("name");
  }
}
