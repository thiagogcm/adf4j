package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record Border(@Nullable String color, @Nullable String size) implements AdfMark {

  @Override
  public String type() {
    return "border";
  }
}
