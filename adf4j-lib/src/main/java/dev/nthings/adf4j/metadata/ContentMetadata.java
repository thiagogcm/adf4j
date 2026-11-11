package dev.nthings.adf4j.metadata;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// Source references and outline. Attachment inventories enrich references by file ID or title.
/// Custom renderer output is not analyzed, so references in a body replaced by a callback can
/// still appear. Excerpts keep their source AST.
public record ContentMetadata(
    List<String> pageIds,
    List<String> externalUrls,
    List<MentionReference> mentionRefs,
    List<AttachmentReference> attachmentRefs,
    List<PageTreeReference> pageTreeRefs,
    List<ExcerptIncludeReference> excerptRefs,
    List<ExcerptDefinition> excerpts,
    List<HeadingReference> outline) {

  private static final ContentMetadata EMPTY =
      new ContentMetadata(
          List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

  public ContentMetadata {
    pageIds = pageIds == null ? List.of() : List.copyOf(pageIds);
    externalUrls = externalUrls == null ? List.of() : List.copyOf(externalUrls);
    mentionRefs = mentionRefs == null ? List.of() : List.copyOf(mentionRefs);
    attachmentRefs = attachmentRefs == null ? List.of() : List.copyOf(attachmentRefs);
    pageTreeRefs = pageTreeRefs == null ? List.of() : List.copyOf(pageTreeRefs);
    excerptRefs = excerptRefs == null ? List.of() : List.copyOf(excerptRefs);
    excerpts = excerpts == null ? List.of() : List.copyOf(excerpts);
    outline = outline == null ? List.of() : List.copyOf(outline);
  }

  public static ContentMetadata empty() {
    return EMPTY;
  }

  /// Distinct non-blank file IDs from {@link #attachmentRefs()}, in first-seen order. Skip
  /// attachments not listed here, as they are never embedded. Attachment-macro IDs are those
  /// `AttachmentResolver` is asked to resolve; media IDs are always included, even when a media
  /// node's `url` bypasses `MediaResolver`.
  public Set<String> referencedFileIds() {
    var ids = new LinkedHashSet<String>();
    for (var attachment : attachmentRefs) {
      var fileId = attachment.fileId();
      if (!fileId.isBlank()) {
        ids.add(fileId);
      }
    }
    return Collections.unmodifiableSet(ids);
  }
}
