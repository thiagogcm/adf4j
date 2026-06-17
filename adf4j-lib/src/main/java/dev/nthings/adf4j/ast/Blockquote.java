package dev.nthings.adf4j.ast;

import java.util.List;

/// A `blockquote` block wrapping its child `content` blocks.
public record Blockquote(List<AdfBlock> content) implements AdfBlock {

  public Blockquote {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
