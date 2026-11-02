package dev.nthings.adf4j.ast;

import java.util.List;

/// An `expand`: a collapsible section. `title` is the summary shown on the toggle (empty when the
/// source gave none); `content` is the blocks revealed when expanded. See {@link NestedExpand} for
/// the variant allowed inside other blocks.
public record Expand(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock, BodiedSyncBlockContent, DocContent, LayoutColumnContent {

  public Expand {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "expand";
  }

  public String title() {
    return attrs.text("title", "");
  }
}
