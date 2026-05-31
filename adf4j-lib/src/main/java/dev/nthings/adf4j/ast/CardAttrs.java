package dev.nthings.adf4j.ast;

public record CardAttrs(
    String url, String datasourceId, String localId, String title, Attributes attrs) {

  public CardAttrs {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }
}
