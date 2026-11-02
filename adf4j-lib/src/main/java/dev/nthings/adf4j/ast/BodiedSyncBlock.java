package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A synced block that carries an inlined copy of its content: the `resourceId` reference of
/// {@link SyncBlock} plus the synced `content` blocks themselves. `resourceId` is `null` when
/// the document omitted it.
public record BodiedSyncBlock(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock, DocContent {

  public BodiedSyncBlock {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "bodiedSyncBlock";
  }

  public @Nullable String resourceId() {
    return attrs.text("resourceId");
  }
}
