package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record Panel(@Nullable String panelType, List<AdfBlock> content) implements AdfBlock {

  public Panel {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
