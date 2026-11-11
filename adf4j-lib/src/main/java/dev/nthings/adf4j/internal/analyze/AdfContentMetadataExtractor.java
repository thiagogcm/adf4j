package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfCard;
import dev.nthings.adf4j.ast.AdfExtension;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.AdfMedia;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.ast.Mention;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.internal.ConfluenceMetadata;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Attachments;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Excerpt;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.ExcerptInclude;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.InlineMediaImage;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.PageTree;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.ViewPdf;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.metadata.MentionReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Collects references and excerpt definitions during the {@link AdfNodeWalker} pass. One
/// instance per document.
final class AdfContentMetadataExtractor implements NodeVisitor {

  private final LinkedHashSet<String> pageIds = new LinkedHashSet<>();
  private final LinkedHashSet<String> externalUrls = new LinkedHashSet<>();
  private final LinkedHashSet<MentionReference> mentionRefs = new LinkedHashSet<>();
  private final LinkedHashMap<String, AttachmentFacts> attachmentRefs = new LinkedHashMap<>();
  // Occurrences, not sets: repeated tree and excerpt macros are themselves a signal.
  private final ArrayList<PageTreeReference> pageTreeRefs = new ArrayList<>();
  private final ArrayList<ExcerptIncludeReference> excerptRefs = new ArrayList<>();
  private final ArrayList<ExcerptDefinition> excerpts = new ArrayList<>();
  private final AttachmentInventory inventory;

  AdfContentMetadataExtractor(AttachmentInventory inventory) {
    this.inventory = inventory;
  }

  @Override
  public void visitBlock(AdfBlock block, String path) {
    switch (block) {
      case Media media -> collectMedia(media);
      // Without media, the wrapper's link renders nothing.
      case MediaSingle single when !single.media().isEmpty() -> collectLinkMarks(single.marks());
      case Extension extension -> collectExtension(extension);
      // Bodied macros never expand, so only an excerpt counts, as a definition.
      case BodiedExtension bodied -> collectExcerptDefinition(bodied);
      case AdfCard card -> collectLink(card.url(), card.attrs());
      default -> {}
    }
  }

  @Override
  public void visitInline(AdfInline inline, String path) {
    switch (inline) {
      case Text text -> collectLinkMarks(text.marks());
      case AdfCard card -> collectLink(card.url(), card.attrs());
      case MediaInline media -> collectMedia(media);
      case InlineExtension extension -> collectExtension(extension);
      case Mention mention -> collectMention(mention);
      default -> {}
    }
  }

  ContentMetadata build(List<HeadingReference> outline) {
    var attachments = new ArrayList<AttachmentReference>(attachmentRefs.size());
    attachmentRefs.forEach(
        (fileId, facts) -> attachments.add(facts.reference(fileId, inventory.byFileId(fileId))));
    return new ContentMetadata(
        List.copyOf(pageIds),
        List.copyOf(externalUrls),
        List.copyOf(mentionRefs),
        attachments,
        List.copyOf(pageTreeRefs),
        List.copyOf(excerptRefs),
        List.copyOf(excerpts),
        List.copyOf(outline));
  }

  private void collectMention(Mention mention) {
    var id = Strings.firstNonBlankStripped(mention.id(), mention.localId());
    mentionRefs.add(new MentionReference(id, mention.text()));
  }

  private void collectLinkMarks(List<AdfMark> marks) {
    var link = AdfText.effectiveLink(marks);
    if (link != null) {
      collectLink(link.href(), link.attrs());
    }
  }

  private void collectLink(@Nullable String rawUrl, Attributes attrs) {
    var normalized = Strings.trimToNull(rawUrl);
    if (normalized == null || "#".equals(normalized)) {
      return;
    }
    var pageNodeId = ConfluenceSupport.pageNodeId(normalized, ConfluenceMetadata.from(attrs));
    if (pageNodeId != null) {
      pageIds.add(pageNodeId);
      return;
    }
    externalUrls.add(normalized);
  }

  private void collectMedia(AdfMedia media) {
    collectLinkMarks(media.marks());
    upsertAttachmentRef(media);

    if ("external".equalsIgnoreCase(Strings.trimToNull(media.sourceType()))) {
      var url = Strings.trimToNull(media.url());
      if (url != null) {
        externalUrls.add(url);
      }
    }
  }

  private void upsertAttachmentRef(AdfMedia media) {
    var fileId = Strings.trimToNull(media.id());
    if (fileId == null) {
      return;
    }

    var title = Strings.firstNonBlankStripped(media.fileName(), media.name(), media.alt());
    var mediaType = Strings.firstNonBlankStripped(media.fileMimeType(), media.mediaType());
    var entry = inventory.byFileId(fileId);
    var titleForGuess = title != null || entry == null ? title : entry.title();
    var guessedMediaType = AttachmentReferences.inferMediaType(titleForGuess);
    mergeAttachmentRef(fileId, new AttachmentFacts(title, mediaType, guessedMediaType));
  }

  private void collectExtension(AdfExtension extension) {
    switch (ConfluenceMacro.of(extension)) {
      // A media node in disguise: its file id is referenced like any media id.
      case InlineMediaImage(var media) -> collectMedia(media);
      case PageTree(var reference) -> pageTreeRefs.add(reference);
      case ExcerptInclude(var reference) -> excerptRefs.add(reference);
      // Expands to the inventory, so each entry counts; with none it adds nothing (as viewpdf).
      case Attachments _ -> inventory.entries().forEach(this::upsertAttachmentRef);
      case ViewPdf _ -> {
        var reference = AttachmentReferences.resolve(extension.macroParams(), inventory);
        if (reference != null) {
          upsertAttachmentRef(reference);
        }
      }
      default -> {}
    }
  }

  // Exposed so resolvers can index source pages by excerpt name.
  private void collectExcerptDefinition(BodiedExtension bodied) {
    if (ConfluenceMacro.of(bodied) instanceof Excerpt(var name)) {
      excerpts.add(new ExcerptDefinition(name, bodied.content()));
    }
  }

  private void upsertAttachmentRef(AttachmentReference attachmentReference) {
    mergeAttachmentRef(
        attachmentReference.fileId(),
        new AttachmentFacts(
            Strings.firstNonBlank(attachmentReference.title()),
            Strings.firstNonBlank(attachmentReference.mediaType()),
            null));
  }

  // One entry per file, keyed by the inventory's spelling of the id when there is one.
  private void mergeAttachmentRef(String fileId, AttachmentFacts facts) {
    var entry = inventory.byFileId(fileId);
    attachmentRefs.merge(
        entry == null ? fileId : entry.fileId(), facts, AttachmentFacts::updatedWith);
  }

  // Facts the document states about a file, each null or non-blank. The latest mention wins. The
  // inventory entry fills gaps, but its explicit media type beats a guess from the name.
  private record AttachmentFacts(
      @Nullable String title, @Nullable String mediaType, @Nullable String guessedMediaType) {

    AttachmentFacts updatedWith(AttachmentFacts newer) {
      return new AttachmentFacts(
          Strings.firstNonBlank(newer.title, title),
          Strings.firstNonBlank(newer.mediaType, mediaType),
          Strings.firstNonBlank(newer.guessedMediaType, guessedMediaType));
    }

    AttachmentReference reference(String fileId, @Nullable AttachmentReference entry) {
      if (entry == null) {
        return new AttachmentReference(
            fileId, title, Strings.firstNonBlank(mediaType, guessedMediaType));
      }
      return new AttachmentReference(
          fileId,
          Strings.firstNonBlank(title, entry.title()),
          Strings.firstNonBlank(mediaType, entry.mediaType(), guessedMediaType),
          entry.downloadUrl());
    }
  }
}
