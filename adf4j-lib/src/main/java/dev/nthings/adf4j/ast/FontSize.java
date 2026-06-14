package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record FontSize(@Nullable String fontSize) implements AdfMark {

  @Override
  public String type() {
    return "fontSize";
  }
}
