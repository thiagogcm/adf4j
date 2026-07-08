package dev.nthings.adf4j.metadata;

import org.jspecify.annotations.Nullable;

/// One attachment the document embeds or links: the durable `fileId` (what a download API
/// needs), the human `title` (file name, when known), the `mediaType` (an explicit or
/// extension-inferred MIME type, or `null` when neither is available), and the `downloadUrl`
/// (the attachment's real URL or path, or `null` when unknown). A non-null `downloadUrl` is the
/// default link destination, so no resolver callback is needed to produce working links.
public record AttachmentReference(
    String fileId,
    @Nullable String title,
    @Nullable String mediaType,
    @Nullable String downloadUrl) {

  /// A reference without a known `downloadUrl`.
  public AttachmentReference(String fileId, @Nullable String title, @Nullable String mediaType) {
    this(fileId, title, mediaType, null);
  }
}
