package dev.nthings.adf4j.ast;

public record FontSize(String fontSize) implements AdfMark {

  @Override
  public String type() {
    return "fontSize";
  }
}
