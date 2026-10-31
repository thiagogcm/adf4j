package dev.nthings.adf4j.ast;

import java.util.List;

/// A `caption`: the inline `content` describing the media it accompanies (e.g. a `mediaSingle`).
public record Caption(Attributes attrs, List<AdfInline> content, List<AdfMark> marks)
    implements AdfBlock, MediaSingleContent {

  public Caption {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "caption";
  }
}
