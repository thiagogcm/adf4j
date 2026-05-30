package dev.nthings.adf4j.ast;

public record Underline() implements AdfMark {

  @Override
  public String type() {
    return "underline";
  }
}
