package dev.nthings.adf4j.ast;

public record Border(String color, String size) implements AdfMark {

  @Override
  public String type() {
    return "border";
  }
}
