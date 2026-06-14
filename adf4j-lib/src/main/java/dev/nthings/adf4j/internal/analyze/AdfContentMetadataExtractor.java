package dev.nthings.adf4j.internal.analyze;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.ExternalReference;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.metadata.MentionReference;
import dev.nthings.adf4j.metadata.PageReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.BlockCard;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.CardAttrs;
import dev.nthings.adf4j.confluence.ConfluenceMetadata;
import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.ast.EmbedCard;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.InlineCard;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.Link;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaAttrs;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.Mention;
import dev.nthings.adf4j.ast.MultiBodiedExtension;
import dev.nthings.adf4j.ast.Text;

import org.jspecify.annotations.Nullable;

/**
 * Harvests the four content-reference kinds (page, external, mention, attachment) as the
 * {@link AdfNodeWalker} visits each node, then materializes a {@link ContentMetadata}. Holds the
 * accumulation for one document; create a fresh instance per document.
 */
final class AdfContentMetadataExtractor implements NodeVisitor {

  private final LinkedHashSet<String> pageRefs = new LinkedHashSet<>();
  private final LinkedHashSet<String> externalRefs = new LinkedHashSet<>();
  private final LinkedHashSet<MentionReference> mentionRefs = new LinkedHashSet<>();
  private final LinkedHashMap<String, AttachmentRefBuilder> attachmentRefs = new LinkedHashMap<>();
  // Occurrences, not sets: the counts of tree/excerpt macros are themselves a signal.
  private final ArrayList<PageTreeReference> pageTreeRefs = new ArrayList<>();
  private final ArrayList<ExcerptIncludeReference> excerptRefs = new ArrayList<>();
  private final ArrayList<ExcerptDefinition> excerpts = new ArrayList<>();
  private final ConfluenceRenderContext confluenceContext;

  AdfContentMetadataExtractor(@Nullable ConfluenceRenderContext confluenceContext) {
    this.confluenceContext =
        confluenceContext == null ? ConfluenceRenderContext.empty() : confluenceContext;
  }

  @Override
  public void visitBlock(AdfBlock block) {
    switch (block) {
      case Media media -> {
        collectLinkMarks(media.marks());
        collectMediaAttrs(media.attrs());
      }
      case Extension extension ->
          collectExtension(
              extension.extensionType(), extension.extensionKey(), extension.macroParams(),
              extension.parameters());
      case BodiedExtension bodied -> {
        collectExtension(
            bodied.extensionType(), bodied.extensionKey(), bodied.macroParams(),
            bodied.parameters());
        collectExcerptDefinition(bodied);
      }
      case MultiBodiedExtension mbe ->
          collectExtension(mbe.extensionType(), mbe.extensionKey(), mbe.macroParams(), mbe.parameters());
      case BlockCard blockCard -> collectCardLink(blockCard.attrs());
      case EmbedCard embedCard -> collectCardLink(embedCard.attrs());
      default -> {
        // Other blocks' references (inline/child) arrive via the walk.
      }
    }
  }

  @Override
  public void visitInline(AdfInline inline) {
    switch (inline) {
      case Text text -> collectLinkMarks(text.marks());
      case InlineCard card -> collectCardLink(card.attrs());
      case MediaInline media -> {
        collectLinkMarks(media.marks());
        collectMediaAttrs(media.attrs());
      }
      case InlineExtension extension ->
          collectExtension(
              extension.extensionType(), extension.extensionKey(), extension.macroParams(),
              extension.parameters());
      case Mention mention -> collectMention(mention);
      default -> {
        // Plain inline content carries no references.
      }
    }
  }

  ContentMetadata build(@Nullable List<HeadingReference> outline) {
    var attachments = new ArrayList<AttachmentReference>(attachmentRefs.size());
    for (var builder : attachmentRefs.values()) {
      attachments.add(builder.build());
    }
    return new ContentMetadata(
        pageRefs.stream().map(PageReference::new).toList(),
        externalRefs.stream().map(ExternalReference::new).toList(),
        List.copyOf(mentionRefs),
        attachments,
        List.copyOf(pageTreeRefs),
        List.copyOf(excerptRefs),
        List.copyOf(excerpts),
        outline == null ? List.of() : List.copyOf(outline));
  }

  private void collectMention(Mention mention) {
    var id = firstNonBlank(mention.id(), mention.localId());
    mentionRefs.add(new MentionReference(id, mention.text()));
  }

  private void collectLinkMarks(List<AdfMark> marks) {
    for (var mark : marks) {
      if (mark instanceof Link link) {
        collectLink(link.href(), link.attrs());
      }
    }
  }

  private void collectCardLink(@Nullable CardAttrs attrs) {
    if (attrs == null) {
      return;
    }
    collectLink(attrs.url(), attrs.attrs());
  }

  private void collectLink(@Nullable String rawUrl, Attributes attrs) {
    var normalized = ConfluenceSupport.trimToNull(rawUrl);
    if (normalized == null || "#".equals(normalized)) {
      return;
    }
    var pageNodeId = ConfluenceSupport.pageNodeId(normalized, ConfluenceMetadata.from(attrs));
    if (pageNodeId != null) {
      pageRefs.add(pageNodeId);
      return;
    }
    externalRefs.add(normalized);
  }

  private void collectMediaAttrs(@Nullable MediaAttrs attrs) {
    if (attrs == null) {
      return;
    }

    var fileId = firstNonBlank(attrs.id(), attrs.localId());
    if (fileId != null) {
      upsertAttachmentRef(fileId, attrs);
    }

    if ("external".equalsIgnoreCase(ConfluenceSupport.trimToNull(attrs.type()))) {
      var url = ConfluenceSupport.trimToNull(attrs.url());
      if (url != null) {
        externalRefs.add(url);
      }
    }
  }

  private void upsertAttachmentRef(String fileId, MediaAttrs attrs) {
    var builder = attachmentRefs.computeIfAbsent(fileId, AttachmentRefBuilder::new);

    var title = firstNonBlank(attrs.fileName(), attrs.name(), attrs.alt());
    var mediaType = firstNonBlank(
        attrs.fileMimeType(),
        attrs.mediaType(),
        AttachmentReferences.inferMediaType(title),
        attrs.type());
    if (mediaType != null) {
      builder.mediaType = mediaType;
    }

    if (title != null) {
      builder.title = title;
    }
  }

  private void collectExtension(
      @Nullable String extensionType, @Nullable String extensionKey, MacroParams macroParams,
      Attributes parameters) {
    if (ConfluenceSupport.isInlineMediaImage(extensionType, extensionKey)) {
      // A media node in disguise: its file id is referenced like any media id.
      var mediaAttrs = ConfluenceSupport.inlineMediaImageAttrs(parameters);
      if (mediaAttrs != null) {
        var fileId = mediaAttrs.id();
        if (fileId != null) {
          upsertAttachmentRef(fileId, mediaAttrs);
        }
      }
      return;
    }
    if (!ConfluenceSupport.isConfluenceMacroExtension(extensionType)) {
      return;
    }

    var pageTreeReference = ConfluenceSupport.pageTreeReference(extensionKey, macroParams);
    if (pageTreeReference != null) {
      pageTreeRefs.add(pageTreeReference);
      return;
    }

    var excerptIncludeReference =
        ConfluenceSupport.excerptIncludeReference(extensionKey, macroParams);
    if (excerptIncludeReference != null) {
      excerptRefs.add(excerptIncludeReference);
      return;
    }

    if ("attachments".equals(extensionKey)) {
      // The macro expands to the supplied inventory, so each entry is referenced; without a context
      // the expansion is unknown and contributes nothing (the same seed-the-context caveat as viewpdf).
      for (var reference : confluenceContext.attachmentReferencesByTitle().values()) {
        upsertAttachmentRef(reference);
      }
      return;
    }

    if (!"viewpdf".equals(extensionKey)) {
      return;
    }

    var attachmentReference = AttachmentReferences.resolve(macroParams, confluenceContext);
    if (attachmentReference == null
        || attachmentReference.fileId() == null
        || attachmentReference.fileId().isBlank()) {
      return;
    }
    upsertAttachmentRef(attachmentReference);
  }

  // The marked region an `excerpt` macro defines, exposed so resolvers can index source pages.
  private void collectExcerptDefinition(BodiedExtension bodied) {
    if (!ConfluenceSupport.isConfluenceMacroExtension(bodied.extensionType())
        || !"excerpt".equals(bodied.extensionKey())) {
      return;
    }
    excerpts.add(new ExcerptDefinition(
        ConfluenceSupport.excerptName(bodied.macroParams()), bodied.content()));
  }

  private void upsertAttachmentRef(AttachmentReference attachmentReference) {
    var builder =
        attachmentRefs.computeIfAbsent(attachmentReference.fileId(), AttachmentRefBuilder::new);

    if (attachmentReference.title() != null && !attachmentReference.title().isBlank()) {
      builder.title = attachmentReference.title();
    }
    if (attachmentReference.mediaType() != null && !attachmentReference.mediaType().isBlank()) {
      builder.mediaType = attachmentReference.mediaType();
    }
  }

  // First non-blank candidate, stripped, or null when all are blank.
  private static @Nullable String firstNonBlank(@Nullable String... candidates) {
    return Stream.of(candidates)
        .map(ConfluenceSupport::trimToNull)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static final class AttachmentRefBuilder {

    final String fileId;
    @Nullable String title;
    @Nullable String mediaType;

    AttachmentRefBuilder(String fileId) {
      this.fileId = fileId;
    }

    AttachmentReference build() {
      return new AttachmentReference(fileId, title, mediaType);
    }
  }
}
