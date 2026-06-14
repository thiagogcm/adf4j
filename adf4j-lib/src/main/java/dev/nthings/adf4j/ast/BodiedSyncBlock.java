package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record BodiedSyncBlock(@Nullable String resourceId, List<AdfBlock> content)
    implements AdfBlock {

  public BodiedSyncBlock {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
