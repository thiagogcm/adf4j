package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A `taskItem`: one checklist entry with inline `content`. `state` is the checkbox state: `DONE`
/// (case-insensitive) renders as checked, anything else (e.g. `TODO`) or `null` as unchecked. See
/// {@link BlockTaskItem} for the block-content variant.
public record TaskItem(Attributes attrs, List<AdfInline> content, List<AdfMark> marks)
    implements AdfBlock, TaskListContent {

  public TaskItem {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "taskItem";
  }

  public @Nullable String state() {
    return attrs.text("state");
  }

  /// This node with a fixed `localId` in place of the generated one.
  public TaskItem withLocalId(String localId) {
    return new TaskItem(attrs.with("localId", localId), content, marks);
  }
}
