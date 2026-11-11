package dev.nthings.adf4j.ast;

import java.util.List;

/// A horizontal divider block with no content.
public record Rule(Attributes attrs, List<AdfMark> marks)
    implements AdfBlock,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        NestedExpandContent,
        PanelContent,
        TableCellContent {

  public Rule {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "rule";
  }
}
