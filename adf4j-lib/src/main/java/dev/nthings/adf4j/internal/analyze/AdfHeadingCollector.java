package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfCard;
import dev.nthings.adf4j.ast.AdfExtension;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.Date;
import dev.nthings.adf4j.ast.Emoji;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.Mention;
import dev.nthings.adf4j.ast.Placeholder;
import dev.nthings.adf4j.ast.Status;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.ast.UnknownInline;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.internal.Located;
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Anchor;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.InlineMediaImage;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Toc;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.commonmark.ext.heading.anchor.IdGenerator;

/// Collects headings with their source occurrence paths, toc level ranges and explicit anchors
/// during the {@link AdfNodeWalker} pass. {@link #build()} derives each slug. One instance per
/// document.
final class AdfHeadingCollector implements NodeVisitor {

  private final UnknownNodePolicy unknownNodePolicy;
  private final List<Located<Heading>> headings = new ArrayList<>();
  private final Set<Integer> tocReferencedLevels = new HashSet<>();
  private final Set<String> explicitAnchors = new HashSet<>();

  AdfHeadingCollector(UnknownNodePolicy unknownNodePolicy) {
    this.unknownNodePolicy = unknownNodePolicy;
  }

  @Override
  public void visitBlock(AdfBlock block, String path) {
    switch (block) {
      case Heading heading -> headings.add(new Located<>(heading, path));
      case Extension extension -> recordMacro(extension);
      default -> {}
    }
  }

  @Override
  public void visitInline(AdfInline inline, String path) {
    if (inline instanceof InlineExtension extension) {
      recordMacro(extension);
    }
  }

  // Slugs avoid every explicit anchor, wherever it sits. Repeated headings get stable suffixes
  // from the per-build generator.
  HeadingOutline build() {
    var references = new ArrayList<HeadingReference>();
    var idGenerator = IdGenerator.builder().defaultId("section").build();
    var takenAnchors = new HashSet<>(explicitAnchors);
    var referencesByPath = new HashMap<String, HeadingReference>();

    for (var occurrence : headings) {
      var heading = occurrence.node();
      var level = AdfText.clampHeadingLevel(heading.level());
      var headingText = extractHeadingPlainText(heading.content());
      if (headingText.isBlank()) {
        continue;
      }

      var anchor = HeadingContent.extractAnchorId(heading.content());
      if (anchor == null) {
        anchor = uniqueSlug(idGenerator, headingText, takenAnchors);
      }

      var reference = new HeadingReference(level, headingText, anchor);
      references.add(reference);
      referencesByPath.put(occurrence.path(), reference);
    }

    return HeadingOutline.of(references, referencesByPath, tocReferencedLevels);
  }

  // The generator suffixes only repeats of the same text, so skip ids already taken.
  private static String uniqueSlug(IdGenerator idGenerator, String text, Set<String> taken) {
    var slug = idGenerator.generateId(text);
    while (!taken.add(slug)) {
      slug = idGenerator.generateId(text);
    }
    return slug;
  }

  private void recordMacro(AdfExtension extension) {
    switch (ConfluenceMacro.of(extension)) {
      case Toc(var range) -> {
        for (var level = range.min(); level <= range.max(); level++) {
          tocReferencedLevels.add(level);
        }
      }
      case Anchor(var id) when id != null -> explicitAnchors.add(id);
      default -> {}
    }
  }

  private String extractHeadingPlainText(List<AdfInline> content) {
    var builder = new StringBuilder();
    for (var node : HeadingContent.normalizedHeadingNodes(content)) {
      appendPlainText(builder, plainText(node));
    }
    return Strings.collapseLineBreaks(builder.toString()).trim();
  }

  // Macros count with their fallback label; a custom renderer's answer is unknown here.
  private String plainText(AdfInline node) {
    return switch (node) {
      case Text text -> textLabel(text);
      case HardBreak _ -> " ";
      case Emoji emoji -> AdfText.emojiLabel(emoji);
      case Mention mention -> AdfText.mentionLabel(mention);
      case Status status -> "[" + AdfText.statusLabel(status) + "]";
      case AdfCard card ->
          Objects.requireNonNullElse(
              Strings.firstNonBlank(card.title(), card.url()),
              "[" + AdfText.INLINE_CARD_LABEL + "]");
      case MediaInline media -> AdfText.mediaAlt(media);
      case Date date -> AdfText.dateFromTimestamp(date.timestamp());
      case Placeholder placeholder -> placeholder.text();
      case InlineExtension extension -> extensionText(extension);
      case UnknownInline unknown ->
          switch (unknownNodePolicy) {
            case PLACEHOLDER -> "[" + AdfText.unsupportedLabel(unknown) + "]";
            case PRESERVE_RAW -> unknown.rawJson();
            case SKIP, FAIL -> "";
          };
    };
  }

  // A link over blank text shows its href.
  private static String textLabel(Text text) {
    var link = text.text().isBlank() ? AdfText.effectiveLink(text.marks()) : null;
    var href = link == null ? null : Strings.firstNonBlank(link.href());
    return href == null ? text.text() : href;
  }

  private static String extensionText(InlineExtension extension) {
    if (ConfluenceMacro.of(extension) instanceof InlineMediaImage(var media)) {
      return AdfText.mediaAlt(media);
    }
    var text = extension.text();
    return text != null && !text.isBlank()
        ? text
        : "[" + AdfText.extensionPlaceholder(extension) + "]";
  }

  /// Appends a fragment, adding one space only where two sources would fuse without whitespace
  /// (so an image alt before `"Title"` slugs to `icon-title`). Existing whitespace is left alone.
  private static void appendPlainText(StringBuilder builder, String fragment) {
    if (fragment.isEmpty()) {
      return;
    }
    if (!builder.isEmpty()
        && !Character.isWhitespace(builder.charAt(builder.length() - 1))
        && !Character.isWhitespace(fragment.charAt(0))) {
      builder.append(' ');
    }
    builder.append(fragment);
  }
}
