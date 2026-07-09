package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.internal.analyze.HeadingOutline;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.MarkdownOptions;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Per-render state shared by the whole traversal: the precomputed outline, the conversion's
/// {@link MarkdownOptions}, the two mutable per-render sinks, and an attachment index keyed by
/// normalized file id (built once, so the per-media-node lookup is O(1)). The moving cursor
/// lives in {@link RendererState}.
record RenderContext(
    HeadingOutline headingOutline,
    MarkdownOptions options,
    MacroDiagnostics macroDiagnostics,
    UnresolvedTracker unresolvedTracker,
    Map<String, AttachmentReference> attachmentsByFileId) {

  static RenderContext from(MarkdownOptions options, @Nullable HeadingOutline headingOutline) {
    var requiredOptions = Objects.requireNonNull(options, "options");
    return new RenderContext(
        Objects.requireNonNullElseGet(headingOutline, HeadingOutline::empty),
        requiredOptions,
        new MacroDiagnostics(),
        new UnresolvedTracker(),
        indexByFileId(requiredOptions));
  }

  /// The attachment whose `fileId` matches (trimmed, case-insensitively), or `null`.
  @Nullable AttachmentReference attachmentByFileId(@Nullable String fileId) {
    if (fileId == null || fileId.isBlank()) {
      return null;
    }
    return attachmentsByFileId.get(normalizeFileId(fileId));
  }

  // First entry per file id wins, matching ConfluenceRenderContext.attachmentByFileId.
  private static Map<String, AttachmentReference> indexByFileId(MarkdownOptions options) {
    var references = options.confluenceContext().attachmentReferencesByTitle();
    if (references.isEmpty()) {
      return Map.of();
    }
    var index = new LinkedHashMap<String, AttachmentReference>();
    for (var reference : references.values()) {
      index.putIfAbsent(normalizeFileId(reference.fileId()), reference);
    }
    return index;
  }

  private static String normalizeFileId(String fileId) {
    return fileId.strip().toLowerCase(Locale.ROOT);
  }
}
