package dev.nthings.adf4j.ast;

import java.util.List;

/// An attachment embedded in the flow of text rather than as its own block: the inline
/// counterpart of {@link Media}, carrying the same {@link MediaAttrs} (file media needs a
/// `MediaResolver`; external media has a direct `attrs.url()`). `marks` decorate this item.
public record MediaInline(MediaAttrs attrs, List<AdfMark> marks) implements AdfInline {

  public MediaInline {
    marks = marks == null ? List.of() : List.copyOf(marks);
  }
}
