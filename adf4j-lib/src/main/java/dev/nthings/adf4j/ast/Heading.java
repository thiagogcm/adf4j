package dev.nthings.adf4j.ast;

import java.util.List;

/// A `heading` block. `level` is the ADF heading level (1 to 6); out-of-range values are kept
/// as-is here and clamped to the 1 to 6 range only when rendered. `content` is the heading's inline
/// text; `marks` are block-level marks on the whole heading.
public record Heading(Attributes attrs, List<AdfInline> content, List<AdfMark> marks)
    implements AdfBlock,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        NestedExpandContent,
        PanelContent,
        TableCellContent {

  public Heading {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "heading";
  }

  public int level() {
    return attrs.integer("level", 1);
  }

  /// This heading with `marks` in place of any it has.
  public Heading withMarks(AdfMark... marks) {
    return new Heading(attrs, content, List.of(marks));
  }
}
