package dev.nthings.adf4j.ast;

import java.util.List;

/// A `blockquote` block wrapping its child `content` blocks.
public record Blockquote(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        NestedExpandContent,
        TableCellContent {

  public Blockquote {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "blockquote";
  }
}
