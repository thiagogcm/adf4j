package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A synced block that carries an inlined copy of its content: the `resourceId` reference of
/// {@link SyncBlock} plus the synced `content` blocks themselves. `resourceId` is `null` when
/// the document omitted it.
public record BodiedSyncBlock(@Nullable String resourceId, List<AdfBlock> content)
    implements AdfBlock {

  public BodiedSyncBlock {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
