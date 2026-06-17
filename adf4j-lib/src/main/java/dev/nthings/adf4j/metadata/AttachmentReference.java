package dev.nthings.adf4j.metadata;

import org.jspecify.annotations.Nullable;

/// One attachment the document embeds or links: the durable `fileId` (what a download API
/// needs), the human `title` (file name, when known), and the `mediaType` (an explicit or
/// extension-inferred MIME type, or `null` when neither is available).
public record AttachmentReference(
    String fileId, @Nullable String title, @Nullable String mediaType) {}
