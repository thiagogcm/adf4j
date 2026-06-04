package dev.nthings.adf4j.internal.analyze;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.metadata.ExternalReference;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.metadata.MentionReference;
import dev.nthings.adf4j.metadata.PageReference;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.BlockCard;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.CardAttrs;
import dev.nthings.adf4j.confluence.ConfluenceMetadata;
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
  private final Map<String, AttachmentReference> attachmentReferencesByTitle;

  AdfContentMetadataExtractor(Map<String, AttachmentReference> attachmentReferencesByTitle) {
    this.attachmentReferencesByTitle =
        attachmentReferencesByTitle == null ? Map.of() : attachmentReferencesByTitle;
  }

  @Override
  public void visitBlock(AdfBlock block) {
    switch (block) {
      case Media media -> {
        collectLinkMarks(media.marks());
        collectMediaAttrs(media.attrs());
      }
      case Extension extension ->
          collectExtension(extension.extensionType(), extension.extensionKey(), extension.macroParams());
      case BodiedExtension bodied ->
          collectExtension(bodied.extensionType(), bodied.extensionKey(), bodied.macroParams());
      case MultiBodiedExtension mbe ->
          collectExtension(mbe.extensionType(), mbe.extensionKey(), mbe.macroParams());
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
          collectExtension(extension.extensionType(), extension.extensionKey(), extension.macroParams());
      case Mention mention -> collectMention(mention);
      default -> {
        // Plain inline content carries no references.
      }
    }
  }

  ContentMetadata build(List<HeadingReference> outline) {
    var attachments = new ArrayList<AttachmentReference>(attachmentRefs.size());
    for (var builder : attachmentRefs.values()) {
      attachments.add(builder.build());
    }
    return new ContentMetadata(
        pageRefs.stream().map(PageReference::new).toList(),
        externalRefs.stream().map(ExternalReference::new).toList(),
        List.copyOf(mentionRefs),
        attachments,
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

  private void collectCardLink(CardAttrs attrs) {
    if (attrs == null) {
      return;
    }
    collectLink(attrs.url(), attrs.attrs());
  }

  private void collectLink(String rawUrl, Attributes attrs) {
    var normalized = trimToNull(rawUrl);
    if (normalized == null || "#".equals(normalized)) {
      return;
    }
    var metadata = ConfluenceMetadata.from(attrs);
    var pageNodeId = resolvePageNodeId(normalized, metadata);
    if (pageNodeId != null) {
      pageRefs.add(pageNodeId);
      return;
    }
    externalRefs.add(normalized);
  }

  private String resolvePageNodeId(String normalizedUrl, ConfluenceMetadata metadata) {
    var inferredNodeId = ConfluenceSupport.pageId(normalizedUrl);
    var metadataNodeId = metadata == null
        ? null
        : firstNonBlank(metadata.pageId(), metadata.contentId(), metadata.id());
    var linkType = metadata == null ? null : trimToNull(metadata.linkType());
    if (!"page".equalsIgnoreCase(linkType)
        && inferredNodeId == null
        && metadataNodeId == null) {
      return null;
    }
    return Stream.of(inferredNodeId, metadataNodeId)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private void collectMediaAttrs(MediaAttrs attrs) {
    if (attrs == null) {
      return;
    }

    var fileId = firstNonBlank(attrs.id(), attrs.localId());
    if (fileId != null) {
      upsertAttachmentRef(fileId, attrs);
    }

    if ("external".equalsIgnoreCase(trimToNull(attrs.type()))) {
      var url = trimToNull(attrs.url());
      if (url != null) {
        externalRefs.add(url);
      }
    }
  }

  private void upsertAttachmentRef(String fileId, MediaAttrs attrs) {
    var builder = attachmentRefs.computeIfAbsent(fileId, AttachmentRefBuilder::new);

    var mediaType = firstNonBlank(attrs.fileMimeType(), attrs.mediaType(), attrs.type());
    if (mediaType != null) {
      builder.mediaType = mediaType;
    }

    var fileName = firstNonBlank(attrs.fileName(), attrs.name());
    if (fileName != null) {
      builder.title = fileName;
    }
  }

  private void collectExtension(String extensionType, String extensionKey, MacroParams macroParams) {
    if (!"com.atlassian.confluence.macro.core".equals(extensionType)
        || !"viewpdf".equals(extensionKey)) {
      return;
    }

    var attachmentReference = AttachmentReferences.resolve(macroParams, attachmentReferencesByTitle);
    if (attachmentReference == null
        || attachmentReference.fileId() == null
        || attachmentReference.fileId().isBlank()) {
      return;
    }
    upsertAttachmentRef(attachmentReference);
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
  private static String firstNonBlank(String... candidates) {
    return Stream.of(candidates)
        .map(AdfContentMetadataExtractor::trimToNull)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    var stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  private static final class AttachmentRefBuilder {

    final String fileId;
    String title;
    String mediaType;

    AttachmentRefBuilder(String fileId) {
      this.fileId = fileId;
    }

    AttachmentReference build() {
      return new AttachmentReference(fileId, title, mediaType);
    }
  }
}
