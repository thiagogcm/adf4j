package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A `taskItem` whose `content` is block-level rather than inline (the {@link TaskItem} variant
/// carrying blocks). `state` follows the same convention: `DONE` (case-insensitive) is checked,
/// anything else or `null` unchecked.
public record BlockTaskItem(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock, TaskListContent {

  public BlockTaskItem {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "blockTaskItem";
  }

  public @Nullable String state() {
    return attrs.text("state");
  }
}
