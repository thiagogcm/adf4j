package dev.nthings.adf4j.ast;

import java.util.List;

/// An inline attachment: the inline counterpart of {@link Media}, with the same {@link AdfMedia}
/// fields. File media needs a `MediaResolver`; external media has {@link #url()}. `marks` decorate
/// this item.
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
