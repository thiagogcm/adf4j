package dev.nthings.adf4j.internal.render;

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
import dev.nthings.adf4j.metadata.PageReference;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.BlockCard;
import dev.nthings.adf4j.ast.BlockTaskItem;
import dev.nthings.adf4j.ast.Blockquote;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.Caption;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.CardAttrs;
import dev.nthings.adf4j.confluence.ConfluenceMetadata;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.EmbedCard;
import dev.nthings.adf4j.ast.Expand;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.ExtensionFrame;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.InlineCard;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.LayoutColumn;
import dev.nthings.adf4j.ast.LayoutSection;
import dev.nthings.adf4j.ast.Link;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaAttrs;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.ast.MultiBodiedExtension;
import dev.nthings.adf4j.ast.NestedExpand;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Panel;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;
import dev.nthings.adf4j.ast.TaskItem;
import dev.nthings.adf4j.ast.TaskList;
import dev.nthings.adf4j.ast.Text;

public final class AdfContentMetadataExtractor {

  public ContentMetadata extract(
      AdfDocument document, MarkdownOptions options, List<HeadingReference> outline) {
    if (document == null) {
      return ContentMetadata.empty();
    }

    var confluenceContext = options.context();
    var state = new State(confluenceContext.attachmentReferencesByTitle());
    for (var block : document.content()) {
      collectBlock(block, state);
    }
    var attachmentRefs = new ArrayList<AttachmentReference>(state.attachmentRefs.size());
    for (var builder : state.attachmentRefs.values()) {
      attachmentRefs.add(builder.build());
    }
    return new ContentMetadata(
        state.pageRefs.stream().map(PageReference::new).toList(),
        state.externalRefs.stream().map(ExternalReference::new).toList(),
        attachmentRefs,
        outline == null ? List.of() : List.copyOf(outline));
  }

  private void collectBlock(AdfBlock block, State state) {
    switch (block) {
      case Paragraph paragraph -> collectInlines(paragraph.content(), state);
      case Heading heading -> collectInlines(heading.content(), state);
      case Blockquote bq -> bq.content().forEach(child -> collectBlock(child, state));
      case Panel panel -> panel.content().forEach(child -> collectBlock(child, state));
      case BulletList bl -> bl.content().forEach(item -> collectBlock(item, state));
      case OrderedList ol -> ol.content().forEach(item -> collectBlock(item, state));
      case ListItem item -> item.content().forEach(child -> collectBlock(child, state));
      case TaskList tl -> tl.content().forEach(child -> collectBlock(child, state));
      case TaskItem taskItem -> collectInlines(taskItem.content(), state);
      case BlockTaskItem bti -> bti.content().forEach(child -> collectBlock(child, state));
      case DecisionList dl -> dl.content().forEach(item -> collectBlock(item, state));
      case DecisionItem decision -> collectInlines(decision.content(), state);
      case Table table -> table.content().forEach(row -> collectBlock(row, state));
      case TableRow row -> row.content().forEach(cell -> collectBlock(cell, state));
      case TableCell cell -> cell.content().forEach(child -> collectBlock(child, state));
      case Expand expand -> expand.content().forEach(child -> collectBlock(child, state));
      case NestedExpand expand -> expand.content().forEach(child -> collectBlock(child, state));
      case LayoutSection layout -> layout.content().forEach(column -> collectBlock(column, state));
      case LayoutColumn column -> column.content().forEach(child -> collectBlock(child, state));
      case MediaSingle mediaSingle ->
          mediaSingle.content().forEach(child -> collectBlock(child, state));
      case MediaGroup mediaGroup -> mediaGroup.content().forEach(child -> collectBlock(child, state));
      case Media media -> {
        collectMediaMarks(media.marks(), state);
        collectMediaAttrs(media.attrs(), state);
      }
      case Caption caption -> collectInlines(caption.content(), state);
      case Extension extension ->
          collectExtension(extension.extensionType(), extension.extensionKey(), extension.macroParams(), state);
      case BodiedExtension bodied -> {
        collectExtension(bodied.extensionType(), bodied.extensionKey(), bodied.macroParams(), state);
        bodied.content().forEach(child -> collectBlock(child, state));
      }
      case MultiBodiedExtension mbe -> {
        collectExtension(mbe.extensionType(), mbe.extensionKey(), mbe.macroParams(), state);
        mbe.content().forEach(child -> collectBlock(child, state));
      }
      case ExtensionFrame frame -> frame.content().forEach(child -> collectBlock(child, state));
      case BodiedSyncBlock sync -> sync.content().forEach(child -> collectBlock(child, state));
      case BlockCard blockCard -> collectCardLink(blockCard.attrs(), state);
      case EmbedCard embedCard -> collectCardLink(embedCard.attrs(), state);
      default -> {
        // Rule, SyncBlock, CodeBlock, UnknownBlock — no references to collect.
      }
    }
  }

  private void collectInlines(List<AdfInline> inlines, State state) {
    for (var inline : inlines) {
      collectInline(inline, state);
    }
  }

  private void collectInline(AdfInline inline, State state) {
    switch (inline) {
      case Text text -> collectLinkMarks(text.marks(), state);
      case InlineCard card -> collectCardLink(card.attrs(), state);
      case MediaInline media -> {
        collectMediaMarks(media.marks(), state);
        collectMediaAttrs(media.attrs(), state);
      }
      case InlineExtension extension ->
          collectExtension(extension.extensionType(), extension.extensionKey(), extension.macroParams(), state);
      default -> {
        // Plain inline content carries no references.
      }
    }
  }

  private void collectLinkMarks(List<AdfMark> marks, State state) {
    for (var mark : marks) {
      if (mark instanceof Link link) {
        collectLink(link.href(), link.attrs(), state);
      }
    }
  }

  private void collectMediaMarks(List<AdfMark> marks, State state) {
    for (var mark : marks) {
      if (mark instanceof Link link) {
        collectLink(link.href(), link.attrs(), state);
      }
    }
  }

  private void collectCardLink(CardAttrs attrs, State state) {
    if (attrs == null) {
      return;
    }
    collectLink(attrs.url(), attrs.attrs(), state);
  }

  private void collectLink(String rawUrl, Attributes attrs, State state) {
    var normalized = trimToNull(rawUrl);
    if (normalized == null || "#".equals(normalized)) {
      return;
    }
    var metadata = ConfluenceMetadata.from(attrs);
    var pageNodeId = resolvePageNodeId(normalized, metadata);
    if (pageNodeId != null) {
      state.pageRefs.add(pageNodeId);
      return;
    }
    state.externalRefs.add(normalized);
  }

  private String resolvePageNodeId(String normalizedUrl, ConfluenceMetadata metadata) {
    var inferredNodeId = ConfluenceSupport.pageId(normalizedUrl);
    var metadataNodeId = metadata == null
        ? null
        : Stream.of(
                trimToNull(metadata.pageId()),
                trimToNull(metadata.contentId()),
                trimToNull(metadata.id()))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
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

  private void collectMediaAttrs(MediaAttrs attrs, State state) {
    if (attrs == null) {
      return;
    }

    var fileId = Stream.of(trimToNull(attrs.id()), trimToNull(attrs.localId()))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
    if (fileId != null) {
      upsertAttachmentRef(fileId, attrs, state.attachmentRefs);
    }

    if ("external".equalsIgnoreCase(trimToNull(attrs.type()))) {
      var url = trimToNull(attrs.url());
      if (url != null) {
        state.externalRefs.add(url);
      }
    }
  }

  private void upsertAttachmentRef(
      String fileId, MediaAttrs attrs, LinkedHashMap<String, AttachmentRefBuilder> attachmentRefs) {
    var builder = attachmentRefs.computeIfAbsent(fileId, AttachmentRefBuilder::new);

    var mediaType = Stream.of(
            trimToNull(attrs.fileMimeType()),
            trimToNull(attrs.mediaType()),
            trimToNull(attrs.type()))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
    if (mediaType != null) {
      builder.mediaType = mediaType;
    }

    var fileName = Stream.of(trimToNull(attrs.fileName()), trimToNull(attrs.name()))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
    if (fileName != null) {
      builder.title = fileName;
    }
  }

  private void collectExtension(
      String extensionType, String extensionKey, MacroParams macroParams, State state) {
    if (!"com.atlassian.confluence.macro.core".equals(extensionType)
        || !"viewpdf".equals(extensionKey)) {
      return;
    }

    var attachmentReference =
        AttachmentReferences.resolve(macroParams, state.attachmentReferencesByTitle);
    if (attachmentReference == null
        || attachmentReference.fileId() == null
        || attachmentReference.fileId().isBlank()) {
      return;
    }
    upsertAttachmentRef(attachmentReference, state.attachmentRefs);
  }

  private void upsertAttachmentRef(
      AttachmentReference attachmentReference,
      LinkedHashMap<String, AttachmentRefBuilder> attachmentRefs) {
    var builder =
        attachmentRefs.computeIfAbsent(attachmentReference.fileId(), AttachmentRefBuilder::new);

    if (attachmentReference.title() != null && !attachmentReference.title().isBlank()) {
      builder.title = attachmentReference.title();
    }
    if (attachmentReference.mediaType() != null && !attachmentReference.mediaType().isBlank()) {
      builder.mediaType = attachmentReference.mediaType();
    }
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    var stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  private static final class State {

    final LinkedHashSet<String> pageRefs = new LinkedHashSet<>();
    final LinkedHashSet<String> externalRefs = new LinkedHashSet<>();
    final LinkedHashMap<String, AttachmentRefBuilder> attachmentRefs = new LinkedHashMap<>();
    final Map<String, AttachmentReference> attachmentReferencesByTitle;

    State(Map<String, AttachmentReference> attachmentReferencesByTitle) {
      this.attachmentReferencesByTitle = attachmentReferencesByTitle == null
          ? Map.of()
          : attachmentReferencesByTitle;
    }
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
