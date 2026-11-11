package dev.nthings.adf4j.ast;

/// An inline-comment (`annotation`) mark. It has no visible formatting, so it is dropped from the
/// output and the underlying text kept.
public record Annotation(Attributes attrs) implements TextMark, MediaMark {

  @Override
  public String type() {
    return "annotation";
  }
}
