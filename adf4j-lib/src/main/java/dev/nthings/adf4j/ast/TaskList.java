package dev.nthings.adf4j.ast;

import java.util.List;

/// A `taskList` (action/checklist): `content` holds its {@link TaskItem} / {@link BlockTaskItem}
/// entries, and may also contain nested {@link TaskList}s.
public record TaskList(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        ListItemContent,
        NestedExpandContent,
        PanelContent,
        TableCellContent,
        TaskListContent {

  public TaskList {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "taskList";
  }

  /// The {@link TaskItem}, {@link BlockTaskItem} and nested {@link TaskList} children; `content`
  /// keeps any other child.
  public List<TaskListContent> items() {
    return ChildNodes.ofType(content, TaskListContent.class);
  }
}
