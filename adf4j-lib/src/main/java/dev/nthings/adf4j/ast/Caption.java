package dev.nthings.adf4j.ast;

import java.util.List;

/// A `caption`: the inline `content` describing the media it accompanies (e.g. a `mediaSingle`).
public record Caption(List<AdfInline> content) implements AdfBlock {

  public Caption {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
