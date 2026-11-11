package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A checklist entry with inline `content`. `state` `DONE` (case-insensitive) is checked; anything
/// else, including `null`, is unchecked. See {@link BlockTaskItem} for the block-content variant.
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

  /// Sets a fixed `localId` instead of the generated one.
  public TaskItem withLocalId(String localId) {
    return new TaskItem(attrs.with("localId", localId), content, marks);
  }
}
