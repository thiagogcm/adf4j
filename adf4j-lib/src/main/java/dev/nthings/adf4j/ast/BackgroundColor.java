package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record BackgroundColor(@Nullable String color) implements AdfMark {

  @Override
  public String type() {
    return "backgroundColor";
  }
}
