package dev.nthings.adf4j.ast;

/// An inline-comment (`annotation`) mark on text the editor has a comment attached to. Carries no
/// visible formatting and is dropped from the output, leaving the underlying text intact.
public record Annotation() implements AdfMark {

  @Override
  public String type() {
    return "annotation";
  }
}
