package dev.nthings.adf4j.ast;

import java.util.List;

/// A `caption`: the inline `content` describing the media it accompanies (e.g. a `mediaSingle`).
public record Caption(Attributes attrs, List<AdfInline> content, List<AdfMark> marks)
    implements AdfBlock {

  public Caption {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "caption";
  }
}
