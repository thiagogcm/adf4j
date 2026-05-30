package dev.nthings.adf4j.ast;

public record Alignment(String align) implements AdfMark {

  @Override
  public String type() {
    return "alignment";
  }
}
