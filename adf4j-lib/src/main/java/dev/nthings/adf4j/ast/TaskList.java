package dev.nthings.adf4j.ast;

import java.util.List;

/// A `taskList` (action/checklist): `content` holds its {@link TaskItem} / {@link BlockTaskItem}
/// entries, and may also contain nested {@link TaskList}s.
public record TaskList(List<AdfBlock> content) implements AdfBlock {

  public TaskList {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
