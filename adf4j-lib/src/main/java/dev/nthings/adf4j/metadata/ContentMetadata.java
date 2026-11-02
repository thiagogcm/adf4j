package dev.nthings.adf4j.metadata;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// References and outline extracted from an ADF document during conversion.
///
/// `attachmentRefs` (and {@link #referencedFileIds()}) come from two sources:
/// `media`/`mediaInline` nodes contribute their file id unconditionally, while a Confluence
/// attachment macro (`viewpdf`/"view file") contributes one only when it carries a file id or its
/// title resolves against the inventory supplied via `MarkdownOptions.withAttachments(...)`. Supply
/// that inventory before pruning downloads, or a macro-only attachment is silently absent even
/// though the body links it. A reference to an inventoried file carries the entry's `downloadUrl`,
/// and takes its `title` and `mediaType` from the entry when the document lacks them.
///
/// `externalUrls` are the distinct non-page URLs the body links to or embeds, verbatim. Like every
/// reference here, they cover what renders: a subtree the renderer drops contributes nothing.
///
/// `pageIds`, `pageTreeRefs` and `excerptRefs` together classify a document's page dependencies:
/// `pageIds` are the distinct page node ids the body links to, `pageTreeRefs` lists every
/// `pagetree`/`children` macro occurrence, and `excerptRefs` lists every `excerpt-include` macro
/// occurrence (by source-page *title*; the macro stores no page id, so these cannot appear in
/// `pageIds`). A macro with a body renders as a placeholder, so it counts in neither. Empty lists
// mean the document needs no page hierarchy or foreign excerpts to render
/// fully. All are
/// static document facts; which lookups a render's resolvers actually declined is reported per
/// conversion on `MarkdownResult.unresolved()`.
///
/// `excerpts` are the `excerpt` regions this document itself defines (the content an
/// `excerpt-include` on another page embeds), exposed as parsed ADF blocks so an
/// `ExcerptResolver` implementation can index and render them (see {@link ExcerptDefinition}).
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
