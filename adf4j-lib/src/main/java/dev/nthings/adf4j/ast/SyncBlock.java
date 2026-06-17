package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// A reference to a synced block whose content is defined elsewhere: this node carries only the
/// `resourceId` pointing at the source, not the synced blocks themselves. `resourceId` is `null`
/// when the document omitted it. For the variant that also embeds a copy of the content see
/// {@link BodiedSyncBlock}.
public record SyncBlock(@Nullable String resourceId) implements AdfBlock {}
