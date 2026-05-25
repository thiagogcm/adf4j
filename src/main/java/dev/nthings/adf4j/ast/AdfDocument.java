package dev.nthings.adf4j.ast;

import java.util.List;

public record AdfDocument(int version, List<AdfBlock> content) implements AdfNode {

  public AdfDocument {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
