package dev.nthings.adf4j.ast;

import java.util.List;

/// A paragraph of inline `content`. Block-level `marks` (e.g. `alignment`, `indentation`) apply to
/// the whole paragraph; inline marks sit on each {@link Text}.
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
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "paragraph";
  }

  /// This paragraph with `marks` in place of any it has. Most containers refuse alignment and
  /// indentation, which can't combine; only a blockquote refuses `small`. See {@link
  /// ParagraphMark}.
  public Paragraph withMarks(ParagraphMark... marks) {
    return new Paragraph(attrs, content, List.<AdfMark>of(marks));
  }
}
