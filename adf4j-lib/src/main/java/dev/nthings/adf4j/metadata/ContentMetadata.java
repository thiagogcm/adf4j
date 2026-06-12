package dev.nthings.adf4j.metadata;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * References and outline extracted from an ADF document during conversion.
 *
 * <p>{@code attachmentRefs} (and {@link #referencedFileIds()}) come from two sources:
 * {@code media}/{@code mediaInline} nodes contribute their file id unconditionally, while a Confluence
 * attachment macro ({@code viewpdf}/"view file") contributes one only when its title resolves against
 * the references supplied via {@code ConfluenceRenderContext.withAttachmentReferences(...)}. Seed that
 * context before pruning downloads, or a macro-only attachment is silently absent even though the body
 * links it.
 *
 * <p>{@code pageRefs}, {@code pageTreeRefs} and {@code excerptRefs} together classify a document's
 * page dependencies: {@code pageRefs} are the distinct page node ids the body links to,
 * {@code pageTreeRefs} lists every {@code pagetree}/{@code children} macro occurrence, and
 * {@code excerptRefs} lists every {@code excerpt-include} macro occurrence (by source-page
 * <em>title</em> — the macro stores no page id, so these cannot appear in {@code pageRefs}). Empty
 * lists mean the document needs no page hierarchy or foreign excerpts to render fully. All are
 * static document facts; which lookups a render's resolvers actually declined is reported per
 * conversion on {@code MarkdownResult.unresolved()}.
 *
 * <p>{@code excerpts} are the {@code excerpt} regions this document itself defines — the content an
 * {@code excerpt-include} on another page embeds — exposed as parsed ADF blocks so an
 * {@code ExcerptResolver} implementation can index and render them (see {@link ExcerptDefinition}).
 */
public record ContentMetadata(
    List<PageReference> pageRefs,
    List<ExternalReference> externalRefs,
    List<MentionReference> mentionRefs,
    List<AttachmentReference> attachmentRefs,
    List<PageTreeReference> pageTreeRefs,
    List<ExcerptIncludeReference> excerptRefs,
    List<ExcerptDefinition> excerpts,
    List<HeadingReference> outline) {

  private static final ContentMetadata EMPTY = new ContentMetadata(
      List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

  public ContentMetadata {
    pageRefs = pageRefs == null ? List.of() : List.copyOf(pageRefs);
    externalRefs = externalRefs == null ? List.of() : List.copyOf(externalRefs);
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

  /**
   * The distinct, non-blank attachment file ids the body references ({@link #attachmentRefs()} keys),
   * in first-seen order — fetch these and skip attachments that are attached but never embedded.
   * Coverage depends on the options used (see the class note): for attachment macros this is exactly
   * what {@code AttachmentResolver} is asked to resolve; media ids are always included, even when a
   * media node's own {@code url} bypasses {@code MediaResolver}.
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
