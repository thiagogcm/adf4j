package dev.nthings.adf4j.metadata;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** References and outline extracted from an ADF document during conversion. */
public record ContentMetadata(
    List<PageReference> pageRefs,
    List<ExternalReference> externalRefs,
    List<MentionReference> mentionRefs,
    List<AttachmentReference> attachmentRefs,
    List<HeadingReference> outline) {

  private static final ContentMetadata EMPTY =
      new ContentMetadata(List.of(), List.of(), List.of(), List.of(), List.of());

  public ContentMetadata {
    pageRefs = pageRefs == null ? List.of() : List.copyOf(pageRefs);
    externalRefs = externalRefs == null ? List.of() : List.copyOf(externalRefs);
    mentionRefs = mentionRefs == null ? List.of() : List.copyOf(mentionRefs);
    attachmentRefs = attachmentRefs == null ? List.of() : List.copyOf(attachmentRefs);
    outline = outline == null ? List.of() : List.copyOf(outline);
  }

  public static ContentMetadata empty() {
    return EMPTY;
  }

  /**
   * The distinct, non-blank attachment file ids the document body actually references (the
   * {@link #attachmentRefs()} keys), in first-seen order. A downloading consumer can fetch only these
   * and skip attachments that are merely attached to the page but never embedded.
   */
  public Set<String> referencedFileIds() {
    var ids = new LinkedHashSet<String>();
    for (var attachment : attachmentRefs) {
      var fileId = attachment.fileId();
      if (fileId != null && !fileId.isBlank()) {
        ids.add(fileId);
      }
    }
    return Collections.unmodifiableSet(ids);
  }
}
