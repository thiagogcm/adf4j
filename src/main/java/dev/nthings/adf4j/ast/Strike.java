package dev.nthings.adf4j.ast;

public record Strike() implements AdfMark {

  @Override
  public String type() {
    return "strike";
  }
}
