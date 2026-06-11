package dev.nthings.adf4j.metadata;

/**
 * One attachment the document embeds or links: the durable {@code fileId} (what a download API
 * needs), the human {@code title} (file name, when known), and the {@code mediaType} (an explicit or
 * extension-inferred MIME type, or {@code null} when neither is available).
 */
public record AttachmentReference(String fileId, String title, String mediaType) {}
