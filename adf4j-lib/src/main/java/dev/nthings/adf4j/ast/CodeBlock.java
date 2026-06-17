package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// A `codeBlock`. `language` is the source language hint (e.g. `java`, `json`) used for the fence
/// info string, or `null` when none was declared. `text` is the verbatim code, normalized to `""`
/// when absent.
public record CodeBlock(@Nullable String language, String text) implements AdfBlock {

  public CodeBlock {
    text = text == null ? "" : text;
  }
}
