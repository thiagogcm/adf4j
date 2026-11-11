package dev.nthings.adf4j.ast;

import java.util.List;

/// One attachment or external image: the leaf inside a {@link MediaSingle} or {@link MediaGroup},
/// never used alone. File media (`sourceType` `file`/`link`) needs a `MediaResolver` to get a URL;
/// external media has {@link #url()}. `marks` decorate this item only.
public record Media(Attributes attrs, List<AdfMark> marks) implements AdfBlock, AdfMedia {

  public Media {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "media";
  }

  public Media withAlt(String alt) {
    return new Media(attrs.with("alt", alt), marks);
  }

  public Media withSize(int width, int height) {
    return new Media(attrs.with("width", width).with("height", height), marks);
  }

  /// This media with `marks` in place of any it has.
  public Media withMarks(MediaMark... marks) {
    return new Media(attrs, List.<AdfMark>of(marks));
  }
}
