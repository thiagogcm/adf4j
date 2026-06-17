package dev.nthings.adf4j.ast;

import java.util.List;

/// A `layoutSection`: a multi-column row whose `content` is its {@link LayoutColumn}s in left-to-
/// right order.
public record LayoutSection(List<LayoutColumn> content) implements AdfBlock {

  public LayoutSection {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
