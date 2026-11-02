package dev.nthings.adf4j.internal;

import dev.nthings.adf4j.ast.AdfExtension;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.AdfMedia;
import dev.nthings.adf4j.ast.AdfUnknown;
import dev.nthings.adf4j.ast.Emoji;
import dev.nthings.adf4j.ast.Link;
import dev.nthings.adf4j.ast.Mention;
import dev.nthings.adf4j.ast.Status;
import dev.nthings.adf4j.ast.UnknownBlock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Phase-neutral helpers interpreting raw ADF values, shared by the analyze and render phases so
/// both agree on what a node says. The labels are plain text; the renderer adds the escaping.
public final class AdfText {

  /// What an inline card with neither a title nor a URL renders as.
  public static final String INLINE_CARD_LABEL = "Inline card";

  private AdfText() {}

  /// An emoji's glyph, else its short name, else empty.
  public static String emojiLabel(Emoji emoji) {
    return Objects.requireNonNullElse(Strings.firstNonBlank(emoji.text(), emoji.shortName()), "");
  }

  /// A mention's display name, else `@` plus its account id (or local id), else `@unknown`.
  public static String mentionLabel(Mention mention) {
    var text = mention.text();
    if (!text.isBlank()) {
      return text;
    }
    var id = Strings.firstNonBlank(mention.id(), mention.localId());
    return id != null ? "@" + id : "@unknown";
  }

  /// A status lozenge's text, else `status`.
  public static String statusLabel(Status status) {
    var text = status.text();
    return text == null || text.isBlank() ? "status" : text;
  }

  /// The name an extension goes by in placeholders and diagnostics: `type/key`, the key alone, or
  /// `Extension` without one.
  public static String extensionLabel(
      @Nullable String extensionType, @Nullable String extensionKey) {
    if (extensionType != null && extensionKey != null) {
      return extensionType + "/" + extensionKey;
    }
    return extensionKey != null ? extensionKey : "Extension";
  }

  /// The inner text of the `[Extension: …]` placeholder.
  public static String extensionPlaceholder(AdfExtension extension) {
    var label = extensionLabel(extension.extensionType(), extension.extensionKey());
    return "Extension".equals(label) ? label : "Extension: " + label;
  }

  /// The inner text of an unknown node's `[Unsupported: …]` placeholder. A node with a blank type
  /// gets the generic form, so it still leaves a mark in the output.
  public static String unsupportedLabel(AdfUnknown node) {
    var kind = node instanceof UnknownBlock ? "Unsupported" : "Unsupported inline";
    return node.type().isBlank() ? kind : kind + ": " + node.type();
  }

  /// The link mark that applies to a node: the last one, even if it has no `href` and so leaves the
  /// node unlinked. Rendering and metadata both go through this, so they agree.
  public static @Nullable Link effectiveLink(List<AdfMark> marks) {
    Link link = null;
    for (var mark : marks) {
      if (mark instanceof Link found) {
        link = found;
      }
    }
    return link;
  }

  /// Alt text for an image embed: the description if given, else the file name, else `"media"`.
  public static String mediaAlt(AdfMedia media) {
    var label = Strings.firstNonBlank(media.alt(), media.fileName(), media.name());
    return label == null ? "media" : label;
  }

  public static int clampHeadingLevel(int level) {
    return Math.clamp(level, 1, 6);
  }

  // Values under 10^11 are read as epoch seconds, larger ones as epoch milliseconds; a blank or
  // non-numeric timestamp is returned as-is.
  public static String dateFromTimestamp(@Nullable String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return "";
    }

    try {
      var value = Long.parseLong(timestamp);
      // Guard Long.MIN_VALUE before the split: Math.abs would stay negative and steer it wrong.
      var instant =
          value != Long.MIN_VALUE && Math.abs(value) < 100_000_000_000L
              ? Instant.ofEpochSecond(value)
              : Instant.ofEpochMilli(value);
      return instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
    } catch (NumberFormatException | DateTimeException _) {
      return timestamp;
    }
  }
}
