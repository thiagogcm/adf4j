package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record Link(@Nullable String href, @Nullable String title, Attributes attrs)
    implements AdfMark {

  public Link {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }

  @Override
  public String type() {
    return "link";
  }
}
