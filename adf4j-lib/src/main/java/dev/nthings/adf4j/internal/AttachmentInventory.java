package dev.nthings.adf4j.internal;

import dev.nthings.adf4j.metadata.AttachmentReference;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/// Attachments for one render or analyze, from `ConversionOptions.attachments()`. `fileId` is the
/// identity: blank ids are dropped, the first entry per id wins, and matching ignores case and
/// surrounding whitespace. Titles index separately; on a clash the first entry wins the title, but
/// both stay reachable by id.
public final class AttachmentInventory {

  private static final AttachmentInventory UNKNOWN =
      new AttachmentInventory(false, Map.of(), Map.of());

  private final boolean known;
  private final Map<String, AttachmentReference> byFileId;
  private final Map<String, AttachmentReference> byTitle;

  private AttachmentInventory(
      boolean known,
      Map<String, AttachmentReference> byFileId,
      Map<String, AttachmentReference> byTitle) {
    this.known = known;
    this.byFileId = byFileId;
    this.byTitle = byTitle;
  }

  /// `null` means the caller gave no inventory.
  public static AttachmentInventory from(@Nullable List<AttachmentReference> attachments) {
    if (attachments == null) {
      return UNKNOWN;
    }
    var byFileId = new LinkedHashMap<String, AttachmentReference>();
    var byTitle = new LinkedHashMap<String, AttachmentReference>();
    for (var attachment : attachments) {
      var fileId = normalized(attachment.fileId());
      if (fileId != null && byFileId.putIfAbsent(fileId, attachment) == null) {
        var title = normalized(attachment.title());
        if (title != null) {
          byTitle.putIfAbsent(title, attachment);
        }
      }
    }
    return new AttachmentInventory(true, byFileId, byTitle);
  }

  /// True when the caller supplied an inventory, authoritative even when empty.
  public boolean isKnown() {
    return known;
  }

  /// Every entry in document order.
  public Collection<AttachmentReference> entries() {
    return Collections.unmodifiableCollection(byFileId.values());
  }

  public @Nullable AttachmentReference byFileId(@Nullable String fileId) {
    return lookup(byFileId, fileId);
  }

  public @Nullable AttachmentReference byTitle(@Nullable String title) {
    return lookup(byTitle, title);
  }

  private static @Nullable AttachmentReference lookup(
      Map<String, AttachmentReference> index, @Nullable String key) {
    var normalized = normalized(key);
    return normalized == null ? null : index.get(normalized);
  }

  private static @Nullable String normalized(@Nullable String key) {
    var trimmed = Strings.trimToNull(key);
    return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
  }
}
