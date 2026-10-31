package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A `taskItem`: one checklist entry with inline `content`. `state` is the checkbox state: `DONE`
/// (case-insensitive) renders as checked, anything else (e.g. `TODO`) or `null` as unchecked. See
/// {@link BlockTaskItem} for the block-content variant.
public record TaskItem(Attributes attrs, List<AdfInline> content, List<AdfMark> marks)
    implements AdfBlock, TaskListContent {

  public TaskItem {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "taskItem";
  }

  public @Nullable String state() {
    return attrs.text("state");
  }
}
