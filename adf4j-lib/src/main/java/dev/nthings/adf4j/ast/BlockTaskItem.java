package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record BlockTaskItem(@Nullable String state, List<AdfBlock> content) implements AdfBlock {

  public BlockTaskItem {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
