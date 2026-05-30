package dev.nthings.adf4j.ast;

import java.util.List;

public record MediaSingle(
    String layout, String widthType, String width, List<AdfBlock> content) implements AdfBlock {

  public MediaSingle {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
