package dev.nthings.adf4j.ast;

import java.util.List;

/// One attachment or external image: the leaf carrying the media fields of {@link AdfMedia}. Always
/// appears inside a {@link MediaSingle} or {@link MediaGroup} wrapper, never on its own. File media
/// ({@link #sourceType()} `file`/`link`) identifies the asset by id and needs a `MediaResolver` to
/// become a real URL; external media carries a direct {@link #url()}. `marks` decorate this item
/// alone (vs. the wrapper-level marks on {@link MediaSingle}).
public record Media(Attributes attrs, List<AdfMark> marks) implements AdfBlock, AdfMedia {

  public Media {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "media";
  }

  /// This media with alternative text.
  public Media withAlt(String alt) {
    return new Media(attrs.with("alt", alt), marks);
  }

  /// This media with its size in pixels.
  public Media withSize(int width, int height) {
    return new Media(attrs.with("width", width).with("height", height), marks);
  }

  /// This media with `marks` in place of any it has.
  public Media withMarks(AdfMark... marks) {
    return new Media(attrs, List.of(marks));
  }
}
