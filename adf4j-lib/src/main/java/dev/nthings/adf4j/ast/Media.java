package dev.nthings.adf4j.ast;

import java.util.List;

/// One attachment or external image: the leaf carrying the media fields of {@link AdfMedia}. Always
/// appears inside a {@link MediaSingle} or {@link MediaGroup} wrapper, never on its own. File media
/// ({@link #sourceType()} `file`/`link`) identifies the asset by id and needs a `MediaResolver` to
/// become a real URL; external media carries a direct {@link #url()}. `marks` decorate this item
/// alone (vs. the wrapper-level marks on {@link MediaSingle}).
public record Media(Attributes attrs, List<AdfMark> marks)
    implements AdfBlock, AdfMedia, MediaSingleContent {

  public Media {
    attrs = attrs == null ? Attributes.empty() : attrs;
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "media";
  }
}
