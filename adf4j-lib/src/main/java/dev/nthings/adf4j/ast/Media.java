package dev.nthings.adf4j.ast;

import java.util.List;

public record Media(MediaAttrs attrs, List<AdfMark> marks) implements AdfBlock {

  public Media {
    marks = marks == null ? List.of() : List.copyOf(marks);
  }
}
