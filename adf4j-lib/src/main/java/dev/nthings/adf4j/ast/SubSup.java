package dev.nthings.adf4j.ast;

public record SubSup(String subSupType) implements AdfMark {

  @Override
  public String type() {
    return "subsup";
  }
}
