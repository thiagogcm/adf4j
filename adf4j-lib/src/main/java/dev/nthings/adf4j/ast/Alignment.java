package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record Alignment(@Nullable String align) implements AdfMark {

  @Override
  public String type() {
    return "alignment";
  }
}
