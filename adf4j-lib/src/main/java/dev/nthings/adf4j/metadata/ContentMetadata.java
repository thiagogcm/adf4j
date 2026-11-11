package dev.nthings.adf4j.metadata;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// Source references and outline, restricted to supported document content.
/// Attachment inventories enrich references by file ID or title. Custom renderer output is not
/// analyzed, so metadata can include references in a body replaced by a callback.
/// Excerpts retain their source AST for callers that need to render or index them separately.
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

  /// The distinct, non-blank attachment file ids the body references
  /// ({@link #attachmentRefs()} keys), in first-seen order: fetch these and skip attachments that
  /// are attached but never embedded. Coverage depends on the options used (see the class note):
  /// for attachment macros this is exactly what `AttachmentResolver` is asked to resolve; media
  /// ids are always included, even when a media node's own `url` bypasses `MediaResolver`.
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
