package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record TextColor(@Nullable String color) implements AdfMark {

  @Override
  public String type() {
    return "textColor";
  }
}
