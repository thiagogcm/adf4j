package dev.nthings.adf4j.ast;

import java.util.List;

/// A `paragraph` block: a run of inline `content` (text and atoms). `marks` are block-level marks
/// applied to the whole paragraph (e.g. `alignment`, `indentation`), distinct from the inline marks
/// carried on each {@link Text}.
public record Paragraph(Attributes attrs, List<AdfInline> content, List<AdfMark> marks)
    implements AdfBlock,
        BlockTaskItemContent,
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

  public Paragraph {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "paragraph";
  }
}
