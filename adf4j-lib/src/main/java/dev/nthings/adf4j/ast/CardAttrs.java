package dev.nthings.adf4j.ast;

import dev.nthings.adf4j.confluence.ConfluenceMetadata;

public record CardAttrs(
    String url,
    String datasourceId,
    String localId,
    String title,
    ConfluenceMetadata confluenceMetadata) {

  public CardAttrs {
    confluenceMetadata = confluenceMetadata == null ? ConfluenceMetadata.empty() : confluenceMetadata;
  }
}
