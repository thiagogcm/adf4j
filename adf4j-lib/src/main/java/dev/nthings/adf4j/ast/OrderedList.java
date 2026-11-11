package dev.nthings.adf4j.ast;

import java.util.List;

/// A numbered list of {@link ListItem}s; `order` is the number of the first item.
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
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "orderedList";
  }

  public int order() {
    return attrs.integer("order", 1);
  }

  /// The {@link ListItem} children; other children stay only in `content`.
  public List<ListItem> items() {
    return ChildNodes.ofType(content, ListItem.class);
  }

  public OrderedList withOrder(int order) {
    return new OrderedList(attrs.with("order", order), content, marks);
  }
}
