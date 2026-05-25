package dev.nthings.adf4j.ast;

import java.util.List;

public record Text(String text, List<AdfMark> marks) implements AdfInline {

  public Text {
    text = text == null ? "" : text;
    marks = marks == null ? List.of() : List.copyOf(marks);
  }
}
