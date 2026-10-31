package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A reference to a synced block whose content is defined elsewhere: this node carries only the
/// `resourceId` pointing at the source, not the synced blocks themselves. `resourceId` is `null`
/// when the document omitted it. For the variant that also embeds a copy of the content see
/// {@link BodiedSyncBlock}.
public record SyncBlock(Attributes attrs, List<AdfMark> marks) implements AdfBlock, DocContent {

  public SyncBlock {
    attrs = attrs == null ? Attributes.empty() : attrs;
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "syncBlock";
  }

  public @Nullable String resourceId() {
    return attrs.text("resourceId");
  }
}
