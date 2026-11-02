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
    content = List.copyOf(content);
    marks = List.copyOf(marks);
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

  /// This node with a fixed `localId` in place of the generated one.
  public TaskList withLocalId(String localId) {
    return new TaskList(attrs.with("localId", localId), content, marks);
  }
}
