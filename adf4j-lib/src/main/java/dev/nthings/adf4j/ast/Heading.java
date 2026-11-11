package dev.nthings.adf4j.ast;

import java.util.List;

/// A `heading` block. `level` is the ADF level (1 to 6); out-of-range values are kept as-is and
/// clamped to 1 to 6 only when rendered. `marks` are block-level marks on the whole heading.
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

  /// Replaces this heading's `marks`. The schema refuses alignment with indentation; see
  /// {@link HeadingMark}.
  public Heading withMarks(HeadingMark... marks) {
    return new Heading(attrs, content, List.<AdfMark>of(marks));
  }
}
