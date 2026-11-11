package dev.nthings.adf4j.metadata;

import org.jspecify.annotations.Nullable;

/// An attachment the document embeds or links. `fileId` is the durable download ID; `title` is the
/// file name when known; `mediaType` is explicit or extension-inferred (null if unknown);
/// `downloadUrl` is the real URL or path (null if unknown). A non-null `downloadUrl` is the default
/// link destination, so no resolver is needed for working links. In a page's attachment inventory
/// (see `ConversionOptions.withAttachments`), entries are keyed by `fileId`; `title` is optional.
public record AttachmentReference(
    String fileId,
    @Nullable String title,
    @Nullable String mediaType,
    @Nullable String downloadUrl) {

  public AttachmentReference(String fileId, @Nullable String title, @Nullable String mediaType) {
    this(fileId, title, mediaType, null);
  }
}
