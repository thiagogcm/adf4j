package dev.nthings.adf4j.ast;

public record Code() implements AdfMark {

  @Override
  public String type() {
    return "code";
  }
}
