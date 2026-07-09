package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.Date;
import dev.nthings.adf4j.ast.Emoji;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.InlineCard;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.Mention;
import dev.nthings.adf4j.ast.Placeholder;
import dev.nthings.adf4j.ast.Status;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.metadata.HeadingReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.commonmark.ext.heading.anchor.IdGenerator;
import org.jspecify.annotations.Nullable;

/// Records every {@link Heading} and the union of toc-macro level ranges as the
/// {@link AdfNodeWalker} visits each node, then turns them into a {@link HeadingOutline}
/// ({@link #build()} derives the slug/anchor). Holds the accumulation for one document; create a
/// fresh instance per document.
final class AdfHeadingCollector implements NodeVisitor {

  private final List<Heading> headings = new ArrayList<>();
  private final Set<Integer> tocReferencedLevels = new HashSet<>();

  // Standalone single-document collection: one walk over this collector, then build the outline.
  static HeadingOutline collect(@Nullable AdfDocument document) {
    if (document == null) {
      return HeadingOutline.empty();
    }
    var collector = new AdfHeadingCollector();
    AdfNodeWalker.walk(document, List.of(collector));
    return collector.build();
  }

  @Override
  public void visitBlock(AdfBlock block) {
    switch (block) {
      case Heading heading -> headings.add(heading);
      case Extension extension ->
          recordToc(extension.extensionType(), extension.extensionKey(), extension.macroParams());
      default -> {}
    }
  }

  @Override
  public void visitInline(AdfInline inline) {
    if (inline instanceof InlineExtension extension) {
      recordToc(extension.extensionType(), extension.extensionKey(), extension.macroParams());
    }
  }

  // Clamp level, extract text (skipping blank headings), derive an anchor (explicit Confluence
  // anchor,
  // else a commonmark slug). The slug generator is per-build, giving repeated headings stable
  // suffixes.
  HeadingOutline build() {
    var references = new ArrayList<HeadingReference>();
    var idGenerator = IdGenerator.builder().defaultId("section").build();
    var headingsByNode = new IdentityHashMap<Heading, HeadingReference>();

    for (var heading : headings) {
      var level = AdfText.clampHeadingLevel(heading.level());
      var headingText = extractHeadingPlainText(heading.content());
      if (headingText.isBlank()) {
        continue;
      }

      var anchor = HeadingContent.extractAnchorId(heading.content());
      if (anchor == null || anchor.isBlank()) {
        anchor = idGenerator.generateId(headingText);
      }

      var reference = new HeadingReference(level, headingText, anchor);
      references.add(reference);
      headingsByNode.put(heading, reference);
    }

    return HeadingOutline.of(references, headingsByNode, tocReferencedLevels);
  }

  private void recordToc(
      @Nullable String extensionType, @Nullable String extensionKey, MacroParams macroParams) {
    if (!ConfluenceSupport.isConfluenceMacroExtension(extensionType)
        || !"toc".equals(extensionKey)) {
      return;
    }
    var range = TocLevelRange.of(macroParams);
    for (var level = range.min(); level <= range.max(); level++) {
      tocReferencedLevels.add(level);
    }
  }

  private static String extractHeadingPlainText(List<AdfInline> content) {
    var inlineNodes = HeadingContent.normalizedHeadingNodes(content);
    if (inlineNodes.isEmpty()) {
      return "";
    }

    var builder = new StringBuilder();
    for (var node : inlineNodes) {
      switch (node) {
        case Text text -> appendPlainText(builder, text.text());
        case HardBreak _ -> appendPlainText(builder, " ");
        case Emoji emoji ->
            appendPlainText(
                builder,
                Objects.requireNonNullElse(
                    Strings.firstNonBlank(emoji.text(), emoji.shortName()), ""));
        case Mention mention -> appendPlainText(builder, mention.text());
        case Status status -> {
          if (status.text() != null && !status.text().isBlank()) {
            appendPlainText(builder, "[" + status.text() + "]");
          }
        }
        case InlineCard card -> {
          var title = card.attrs().title();
          var label = title != null && !title.isBlank() ? title : card.attrs().url();
          if (label != null) {
            appendPlainText(builder, label);
          }
        }
        case MediaInline media -> appendPlainText(builder, media.attrs().imageAlt());
        case Date date -> appendPlainText(builder, AdfText.dateFromTimestamp(date.timestamp()));
        case Placeholder placeholder -> appendPlainText(builder, placeholder.text());
        default -> {}
      }
    }
    return builder.toString().trim();
  }

  /// Appends a heading-text fragment, inserting one space only when two sources would otherwise
  /// fuse with no whitespace (so an image alt before `"Title"` slugs to `icon-title`, not
  /// `icontitle`). Boundaries that already have whitespace are untouched.
  private static void appendPlainText(StringBuilder builder, String fragment) {
    if (fragment == null || fragment.isEmpty()) {
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
