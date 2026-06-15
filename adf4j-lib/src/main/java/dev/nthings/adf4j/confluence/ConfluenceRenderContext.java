package dev.nthings.adf4j.confluence;

import dev.nthings.adf4j.metadata.AttachmentReference;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Caller-supplied Confluence knowledge for one conversion — today, the page's attachment table, so
 * attachment macros that reference a file by name ({@code viewpdf}, "view file") can be resolved to
 * a durable {@link AttachmentReference}, and the {@code attachments} macro can expand to the page's
 * attachment list. Look up with {@link #attachment(String)}; titles are matched case-insensitively
 * and ignoring surrounding whitespace. Entries without a usable title or {@code fileId} are dropped
 * on construction, and the first entry wins a title collision.
 *
 * <p>{@code attachmentsSupplied} records whether the caller asserted the attachment inventory at
 * all: {@link #withAttachmentReferences} sets it even for an empty iterable, making "this page has
 * no attachments" an authoritative answer (the {@code attachments} macro then renders as nothing)
 * that stays distinct from {@link #empty()} (unknown inventory — the macro keeps its placeholder).
 */
public record ConfluenceRenderContext(
    Map<String, AttachmentReference> attachmentReferencesByTitle, boolean attachmentsSupplied) {

  private static final ConfluenceRenderContext EMPTY = new ConfluenceRenderContext(Map.of(), false);

  public ConfluenceRenderContext {
    attachmentReferencesByTitle = normalized(attachmentReferencesByTitle);
  }

  public static ConfluenceRenderContext empty() {
    return EMPTY;
  }

  /**
   * This context plus the given attachment references, keyed by their own titles. A non-null
   * iterable — even an empty one — marks the inventory as supplied (see the class note).
   */
  public ConfluenceRenderContext withAttachmentReferences(
      Iterable<AttachmentReference> attachmentReferences) {
    var merged = new LinkedHashMap<>(attachmentReferencesByTitle);
    if (attachmentReferences != null) {
      for (var attachmentReference : attachmentReferences) {
        putValid(merged, attachmentReference);
      }
    }
    return new ConfluenceRenderContext(merged, attachmentsSupplied || attachmentReferences != null);
  }

  /** The attachment whose title matches {@code title} (normalized), or {@code null}. */
  public @Nullable AttachmentReference attachment(@Nullable String title) {
    var normalizedTitle = normalizeTitle(title);
    return normalizedTitle == null ? null : attachmentReferencesByTitle.get(normalizedTitle);
  }

  // Re-keys by normalized title, so every constructed instance upholds the lookup invariant
  // regardless of how the caller keyed the map.
  private static Map<String, AttachmentReference> normalized(
      Map<String, AttachmentReference> entries) {
    if (entries == null || entries.isEmpty()) {
      return Map.of();
    }
    var safe = new LinkedHashMap<String, AttachmentReference>();
    for (var reference : entries.values()) {
      putValid(safe, reference);
    }
    return Collections.unmodifiableMap(safe);
  }

  // First valid entry per normalized title wins; entries without a usable title or fileId are
  // dropped.
  private static void putValid(
      Map<String, AttachmentReference> target, AttachmentReference reference) {
    if (reference == null || reference.fileId() == null || reference.fileId().isBlank()) {
      return;
    }
    var title = normalizeTitle(reference.title());
    if (title != null) {
      target.putIfAbsent(title, reference);
    }
  }

  private static @Nullable String normalizeTitle(@Nullable String title) {
    if (title == null) {
      return null;
    }
    var stripped = title.strip();
    return stripped.isEmpty() ? null : stripped.toLowerCase(Locale.ROOT);
  }
}
