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
