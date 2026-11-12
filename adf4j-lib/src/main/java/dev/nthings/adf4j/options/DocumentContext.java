package dev.nthings.adf4j.options;

import dev.nthings.adf4j.metadata.AttachmentReference;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Per-document data. A null inventory is unknown; an empty inventory is authoritative.
public record DocumentContext(
    @Nullable List<AttachmentReference> attachments, @Nullable String title) {
  public DocumentContext {
    attachments = attachments == null ? null : List.copyOf(attachments);
  }

  public static DocumentContext defaults() {
    return new DocumentContext(null, null);
  }

  DocumentContext withAttachments(@Nullable List<AttachmentReference> value) {
    return new DocumentContext(value, title);
  }

  DocumentContext withTitle(@Nullable String value) {
    return new DocumentContext(attachments, value);
  }
}
