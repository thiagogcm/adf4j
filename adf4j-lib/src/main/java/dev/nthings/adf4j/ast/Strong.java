package dev.nthings.adf4j.ast;

public record Strong() implements AdfMark {

  @Override
  public String type() {
    return "strong";
  }
}
