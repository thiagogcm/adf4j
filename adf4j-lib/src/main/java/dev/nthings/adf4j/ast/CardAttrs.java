package dev.nthings.adf4j.ast;

/**
 * The attributes shared by the three smart-card nodes (inline/block/embed): the card's {@code url}
 * (absent for datasource-backed cards), the optional {@code datasourceId}/{@code localId}/
 * {@code title}, and the remaining raw {@code attrs} (e.g. Confluence link metadata).
 */
public record CardAttrs(
    String url, String datasourceId, String localId, String title, Attributes attrs) {

  public CardAttrs {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }
}
