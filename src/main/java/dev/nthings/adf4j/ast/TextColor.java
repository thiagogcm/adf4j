package dev.nthings.adf4j.ast;

public record TextColor(String color) implements AdfMark {

  @Override
  public String type() {
    return "textColor";
  }
}
