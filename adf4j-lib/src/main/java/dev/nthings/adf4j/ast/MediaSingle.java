package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record MediaSingle(
    @Nullable String layout,
    @Nullable String widthType,
    @Nullable String width,
    List<AdfBlock> content,
    List<AdfMark> marks)
    implements AdfBlock {

  public MediaSingle {
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }
}
