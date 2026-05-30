package dev.nthings.adf4j.ast;

public record Breakout() implements AdfMark {

  @Override
  public String type() {
    return "breakout";
  }
}
