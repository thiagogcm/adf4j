package dev.nthings.adf4j.ast;

import java.util.List;

/// One attachment or external image: the leaf carrying the actual {@link MediaAttrs}. Always
/// appears inside a {@link MediaSingle} or {@link MediaGroup} wrapper, never on its own. File
/// media (`attrs.type()` `file`/`link`) identifies the asset by id and needs a `MediaResolver`
/// to become a real URL; external media carries a direct `attrs.url()`. `marks` decorate this
/// item alone (vs. the wrapper-level marks on {@link MediaSingle}).
public record Media(MediaAttrs attrs, List<AdfMark> marks) implements AdfBlock {

  public Media {
    marks = marks == null ? List.of() : List.copyOf(marks);
  }
}
