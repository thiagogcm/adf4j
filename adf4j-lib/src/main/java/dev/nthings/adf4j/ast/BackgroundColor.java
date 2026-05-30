package dev.nthings.adf4j.ast;

public record BackgroundColor(String color) implements AdfMark {

  @Override
  public String type() {
    return "backgroundColor";
  }
}
