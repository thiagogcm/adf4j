package dev.nthings.adf4j.ast;

public record Fragment() implements AdfMark {

  @Override
  public String type() {
    return "fragment";
  }
}
