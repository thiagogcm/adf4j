package dev.nthings.adf4j.ast;

import java.util.List;

public record BodiedSyncBlock(String resourceId, List<AdfBlock> content) implements AdfBlock {

  public BodiedSyncBlock {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
