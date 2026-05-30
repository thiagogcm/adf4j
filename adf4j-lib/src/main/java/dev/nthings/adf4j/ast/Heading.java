package dev.nthings.adf4j.ast;

import java.util.List;

public record Heading(int level, List<AdfInline> content, List<AdfMark> marks) implements AdfBlock {

  public Heading {
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }
}
