package dev.nthings.adf4j.ast;

public record Em() implements AdfMark {

  @Override
  public String type() {
    return "em";
  }
}
