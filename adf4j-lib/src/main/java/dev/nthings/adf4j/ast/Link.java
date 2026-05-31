package dev.nthings.adf4j.ast;

public record Link(String href, String title, Attributes attrs) implements AdfMark {

  public Link {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }

  @Override
  public String type() {
    return "link";
  }
}
