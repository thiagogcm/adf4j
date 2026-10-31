package dev.nthings.adf4j.ast;

/// A fragment (`fragment`) mark grouping nodes into a named, reusable section. Structural metadata
/// with no visible formatting; it is dropped from the output, leaving the underlying content
/// intact.
public record Fragment(Attributes attrs) implements AdfMark {

  public Fragment {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }

  @Override
  public String type() {
    return "fragment";
  }
}
