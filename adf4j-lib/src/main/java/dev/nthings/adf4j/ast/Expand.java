package dev.nthings.adf4j.ast;

import java.util.List;

/// An `expand`: a collapsible section. `title` is the summary shown on the toggle (empty when the
/// source gave none); `content` is the blocks revealed when expanded. See {@link NestedExpand} for
/// the variant allowed inside other blocks.
public record Expand(String title, List<AdfBlock> content) implements AdfBlock {

  public Expand {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
