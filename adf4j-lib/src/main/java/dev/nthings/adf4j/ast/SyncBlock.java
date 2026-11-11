package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A reference to a synced block defined elsewhere: carries only the source `resourceId`, not its
/// content. `resourceId` is `null` when omitted. For the embedded-copy variant see {@link
/// BodiedSyncBlock}.
public record SyncBlock(Attributes attrs, List<AdfMark> marks) implements AdfBlock, DocContent {

  public SyncBlock {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "syncBlock";
  }

  public @Nullable String resourceId() {
    return attrs.text("resourceId");
  }
}
