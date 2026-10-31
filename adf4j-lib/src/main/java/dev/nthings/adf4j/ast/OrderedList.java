package dev.nthings.adf4j.ast;

import java.util.List;

/// An `orderedList`: a numbered list of {@link ListItem} entries. `order` is the starting number
/// of the first item (`1` for a plain list, e.g. `5` to begin at 5).
public record OrderedList(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
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

  public OrderedList {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "orderedList";
  }

  public int order() {
    return attrs.integer("order", 1);
  }

  /// The {@link ListItem} children; `content` keeps any other child.
  public List<ListItem> items() {
    return ChildNodes.ofType(content, ListItem.class);
  }
}
