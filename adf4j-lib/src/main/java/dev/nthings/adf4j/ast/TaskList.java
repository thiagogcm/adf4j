package dev.nthings.adf4j.ast;

import java.util.List;

/// A checklist (action) list of {@link TaskItem}s, {@link BlockTaskItem}s and nested lists.
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

  /// The {@link TaskItem}, {@link BlockTaskItem} and nested {@link TaskList} children; other
  /// children stay only in `content`.
  public List<TaskListContent> items() {
    return ChildNodes.ofType(content, TaskListContent.class);
  }

  /// Sets a fixed `localId` instead of the generated one.
  public TaskList withLocalId(String localId) {
    return new TaskList(attrs.with("localId", localId), content, marks);
  }
}
