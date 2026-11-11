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

/// Shared by analyze and render so both agree on what a node says. Labels are plain text; the
/// renderer adds escaping.
public final class AdfText {

  /// What an inline card with neither a title nor a URL renders as.
  public static final String INLINE_CARD_LABEL = "Inline card";

  private AdfText() {}

  public static String emojiLabel(Emoji emoji) {
    return Objects.requireNonNullElse(Strings.firstNonBlank(emoji.text(), emoji.shortName()), "");
  }

  public static String mentionLabel(Mention mention) {
    var text = mention.text();
    if (!text.isBlank()) {
      return text;
    }
    var id = Strings.firstNonBlank(mention.id(), mention.localId());
    return id != null ? "@" + id : "@unknown";
  }

  public static String statusLabel(Status status) {
    var text = status.text();
    return text == null || text.isBlank() ? "status" : text;
  }

  public static String extensionLabel(
      @Nullable String extensionType, @Nullable String extensionKey) {
    if (extensionType != null && extensionKey != null) {
      return extensionType + "/" + extensionKey;
    }
    return extensionKey != null ? extensionKey : "Extension";
  }

  public static String extensionPlaceholder(AdfExtension extension) {
    var label = extensionLabel(extension.extensionType(), extension.extensionKey());
    return "Extension".equals(label) ? label : "Extension: " + label;
  }

  /// Inner text of the `[Unsupported: …]` placeholder. A blank type keeps the generic form, so
  /// the node still leaves a mark.
  public static String unsupportedLabel(AdfUnknown node) {
    var kind = node instanceof UnknownBlock ? "Unsupported" : "Unsupported inline";
    return node.type().isBlank() ? kind : kind + ": " + node.type();
  }

  /// The last link mark, even one without an `href` (which leaves the node unlinked). Rendering
  /// and metadata both use this, so they agree.
  public static @Nullable Link effectiveLink(List<AdfMark> marks) {
    Link link = null;
    for (var mark : marks) {
      if (mark instanceof Link found) {
        link = found;
      }
    }
    return link;
  }

  public static String mediaAlt(AdfMedia media) {
    var label = Strings.firstNonBlank(media.alt(), media.fileName(), media.name());
    return label == null ? "media" : label;
  }

  public static int clampHeadingLevel(int level) {
    return Math.clamp(level, 1, 6);
  }

  // Under 10^11 is epoch seconds, else epoch milliseconds. Non-numeric input is returned as-is.
  public static String dateFromTimestamp(@Nullable String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return "";
    }

    try {
      var value = Long.parseLong(timestamp);
      // Math.abs(Long.MIN_VALUE) stays negative, so it is excluded explicitly.
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
