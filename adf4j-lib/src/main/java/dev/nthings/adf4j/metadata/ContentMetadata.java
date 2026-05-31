package dev.nthings.adf4j.metadata;

import java.util.List;

/** References and outline extracted from an ADF document during conversion. */
public record ContentMetadata(
    List<PageReference> pageRefs,
    List<ExternalReference> externalRefs,
    List<AttachmentReference> attachmentRefs,
    List<HeadingReference> outline) {

  private static final ContentMetadata EMPTY =
      new ContentMetadata(List.of(), List.of(), List.of(), List.of());

  public ContentMetadata {
    pageRefs = pageRefs == null ? List.of() : List.copyOf(pageRefs);
    externalRefs = externalRefs == null ? List.of() : List.copyOf(externalRefs);
    attachmentRefs = attachmentRefs == null ? List.of() : List.copyOf(attachmentRefs);
    outline = outline == null ? List.of() : List.copyOf(outline);
  }

  public static ContentMetadata empty() {
    return EMPTY;
  }
}
