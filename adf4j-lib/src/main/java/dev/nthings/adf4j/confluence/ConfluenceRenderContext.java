package dev.nthings.adf4j.confluence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.nthings.adf4j.AttachmentReference;
import dev.nthings.adf4j.internal.AttachmentReferences;

public record ConfluenceRenderContext(
    Map<String, AttachmentReference> attachmentReferencesByTitle) {

  private static final ConfluenceRenderContext EMPTY = new ConfluenceRenderContext(Map.of());

  public ConfluenceRenderContext {
    attachmentReferencesByTitle = immutableAttachmentReferencesByTitle(attachmentReferencesByTitle);
  }

  public static ConfluenceRenderContext empty() {
    return EMPTY;
  }

  public ConfluenceRenderContext withAttachmentReferences(
      Iterable<AttachmentReference> attachmentReferences) {
    var safe = new LinkedHashMap<String, AttachmentReference>();
    if (attachmentReferences != null) {
      for (var attachmentReference : attachmentReferences) {
        if (attachmentReference == null) {
          continue;
        }

        var normalizedTitle = AttachmentReferences.normalizeTitle(attachmentReference.title());
        if (normalizedTitle == null
            || attachmentReference.fileId() == null
            || attachmentReference.fileId().isBlank()) {
          continue;
        }

        safe.putIfAbsent(normalizedTitle, attachmentReference);
      }
    }

    return new ConfluenceRenderContext(safe);
  }

  private static Map<String, AttachmentReference> immutableAttachmentReferencesByTitle(
      Map<String, AttachmentReference> attachmentReferencesByTitle) {
    if (attachmentReferencesByTitle == null || attachmentReferencesByTitle.isEmpty()) {
      return Map.of();
    }

    return Collections.unmodifiableMap(new LinkedHashMap<>(attachmentReferencesByTitle));
  }
}
