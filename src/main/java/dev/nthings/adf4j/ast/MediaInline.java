package dev.nthings.adf4j.ast;

import java.util.List;

public record MediaInline(MediaAttrs attrs, List<AdfMark> marks) implements AdfInline {

  public MediaInline {
    marks = marks == null ? List.of() : List.copyOf(marks);
  }
}
