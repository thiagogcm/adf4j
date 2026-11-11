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
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Anchor;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.InlineMediaImage;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Toc;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.commonmark.ext.heading.anchor.IdGenerator;

/// Records every {@link Heading}, the union of toc-macro level ranges and every explicit anchor as
/// the {@link AdfNodeWalker} visits each node, then turns them into a {@link HeadingOutline}
/// ({@link #build()} derives the slug/anchor). Holds the accumulation for one document; create a
/// fresh instance per document.
final class AdfHeadingCollector implements NodeVisitor {

  private final UnknownNodePolicy unknownNodePolicy;
  private final List<Heading> headings = new ArrayList<>();
  private final Set<Integer> tocReferencedLevels = new HashSet<>();
  private final Set<String> explicitAnchors = new HashSet<>();

  AdfHeadingCollector(UnknownNodePolicy unknownNodePolicy) {
    this.unknownNodePolicy = unknownNodePolicy;
  }

  @Override
  public void visitBlock(AdfBlock block, String path) {
    switch (block) {
      case Heading heading -> headings.add(heading);
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

  // Clamp level, extract text (skipping blank headings), derive an anchor (explicit Confluence
  // anchor, else a commonmark slug). The slug generator is per-build, giving repeated headings
  // stable suffixes. Explicit anchors are all known by now, so a slug avoids them wherever they sit
  // in the document.
  HeadingOutline build() {
    var references = new ArrayList<HeadingReference>();
    var idGenerator = IdGenerator.builder().defaultId("section").build();
    var takenAnchors = new HashSet<>(explicitAnchors);
    var headingsByNode = new IdentityHashMap<Heading, HeadingReference>();

    for (var heading : headings) {
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
      headingsByNode.put(heading, reference);
    }

    return HeadingOutline.of(references, headingsByNode, tocReferencedLevels);
  }

  // The generator only suffixes repeats of the same text, so step it past any other taken id.
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

  // The plain text of what the node renders. What a custom extension renderer or a resolver would
  // answer isn't known before the render, so a macro counts with its fallback label.
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

  /// Appends a heading-text fragment, inserting one space only when two sources would otherwise
  /// fuse with no whitespace (so an image alt before `"Title"` slugs to `icon-title`, not
  /// `icontitle`). Boundaries that already have whitespace are untouched.
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
