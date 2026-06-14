package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record CodeBlock(@Nullable String language, String text) implements AdfBlock {

  public CodeBlock {
    text = text == null ? "" : text;
  }
}
