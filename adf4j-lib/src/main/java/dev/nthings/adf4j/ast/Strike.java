package dev.nthings.adf4j.ast;

/// Strikethrough (`strike`) text mark; renders as `~~`.
public record Strike() implements AdfMark {

  @Override
  public String type() {
    return "strike";
  }
}
