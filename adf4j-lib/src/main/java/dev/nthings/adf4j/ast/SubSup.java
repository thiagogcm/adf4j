package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record SubSup(@Nullable String subSupType) implements AdfMark {

  @Override
  public String type() {
    return "subsup";
  }
}
