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
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "decisionList";
  }

  /// The {@link DecisionItem} children; `content` keeps any other child.
  public List<DecisionItem> items() {
    return ChildNodes.ofType(content, DecisionItem.class);
  }

  /// This node with a fixed `localId` in place of the generated one.
  public DecisionList withLocalId(String localId) {
    return new DecisionList(attrs.with("localId", localId), content, marks);
  }
}
