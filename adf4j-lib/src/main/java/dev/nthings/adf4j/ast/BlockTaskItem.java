package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A `taskItem` whose `content` is block-level rather than inline (the {@link TaskItem} variant
/// carrying blocks). `state` follows the same convention: `DONE` (case-insensitive) is checked,
/// anything else or `null` unchecked.
public record BlockTaskItem(@Nullable String state, List<AdfBlock> content) implements AdfBlock {

  public BlockTaskItem {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
