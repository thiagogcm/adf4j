package dev.nthings.adf4j.ast;

public record Annotation() implements AdfMark {

  @Override
  public String type() {
    return "annotation";
  }
}
