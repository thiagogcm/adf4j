package dev.nthings.adf4j.ast;

import java.util.List;

/// A `decisionList`: `content` holds its {@link DecisionItem} entries.
public record DecisionList(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        NestedExpandContent,
        PanelContent,
        TableCellContent {

  public DecisionList {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "decisionList";
  }

  /// The {@link DecisionItem} children; `content` keeps any other child.
  public List<DecisionItem> items() {
    return ChildNodes.ofType(content, DecisionItem.class);
  }
}
