package dev.nthings.adf4j.ast;

import dev.nthings.adf4j.confluence.ConfluenceMetadata;

public record Link(String href, String title, ConfluenceMetadata confluenceMetadata)
    implements AdfMark {

  public Link {
    confluenceMetadata = confluenceMetadata == null ? ConfluenceMetadata.empty() : confluenceMetadata;
  }

  @Override
  public String type() {
    return "link";
  }
}
