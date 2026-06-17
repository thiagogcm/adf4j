package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A `taskItem`: one checklist entry with inline `content`. `state` is the checkbox state: `DONE`
/// (case-insensitive) renders as checked, anything else (e.g. `TODO`) or `null` as unchecked. See
/// {@link BlockTaskItem} for the block-content variant.
public record TaskItem(@Nullable String state, List<AdfInline> content) implements AdfBlock {

  public TaskItem {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
