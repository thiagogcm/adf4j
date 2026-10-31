package dev.nthings.adf4j.ast;

import java.util.List;

/// A `bulletList`: an unordered list whose `content` is its {@link ListItem} entries.
public record BulletList(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock,
        BlockquoteContent,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        ListItemContent,
        NestedExpandContent,
        PanelContent,
        TableCellContent {

  public BulletList {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "bulletList";
  }

  /// The {@link ListItem} children; `content` keeps any other child.
  public List<ListItem> items() {
    return ChildNodes.ofType(content, ListItem.class);
  }
}
