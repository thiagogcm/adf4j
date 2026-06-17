package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A `panel`: a callout box wrapping `content` blocks. `panelType` selects the variant
/// (e.g. `info`/`note`/`warning`/`success`/`error`), or `null` when unspecified.
public record Panel(@Nullable String panelType, List<AdfBlock> content) implements AdfBlock {

  public Panel {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
