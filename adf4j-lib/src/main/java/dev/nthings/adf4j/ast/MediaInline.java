package dev.nthings.adf4j.ast;

import java.util.List;

/// An attachment embedded in the flow of text rather than as its own block: the inline
/// counterpart of {@link Media}, carrying the same {@link AdfMedia} fields (file media needs a
/// `MediaResolver`; external media has a direct {@link #url()}). `marks` decorate this item.
public record MediaInline(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, AdfMedia, InlineContent {

  public MediaInline {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "mediaInline";
  }
}
