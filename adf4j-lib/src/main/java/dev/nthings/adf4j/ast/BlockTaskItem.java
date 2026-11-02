package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A `taskItem` whose `content` is block-level rather than inline (the {@link TaskItem} variant
/// carrying blocks). `state` follows the same convention: `DONE` (case-insensitive) is checked,
/// anything else or `null` unchecked.
public record BlockTaskItem(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock, TaskListContent {

  public BlockTaskItem {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "blockTaskItem";
  }

  public @Nullable String state() {
    return attrs.text("state");
  }

  /// This node with a fixed `localId` in place of the generated one.
  public BlockTaskItem withLocalId(String localId) {
    return new BlockTaskItem(attrs.with("localId", localId), content, marks);
  }
}
