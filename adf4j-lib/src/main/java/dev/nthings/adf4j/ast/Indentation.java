package dev.nthings.adf4j.ast;

public record Indentation(int level) implements AdfMark {

  @Override
  public String type() {
    return "indentation";
  }
}
