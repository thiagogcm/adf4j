package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record TaskItem(@Nullable String state, List<AdfInline> content) implements AdfBlock {

  public TaskItem {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
