package dev.nthings.adf4j.internal;

import java.net.URLConnection;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.ast.MacroParams;

public final class AttachmentReferences {

  private static final Map<String, String> MEDIA_TYPES_BY_EXTENSION = Map.ofEntries(
      Map.entry("bmp", "image/bmp"),
      Map.entry("csv", "text/csv"),
      Map.entry("gif", "image/gif"),
      Map.entry("htm", "text/html"),
      Map.entry("html", "text/html"),
      Map.entry("jpeg", "image/jpeg"),
      Map.entry("jpg", "image/jpeg"),
      Map.entry("json", "application/json"),
      Map.entry("md", "text/markdown"),
      Map.entry("pdf", "application/pdf"),
      Map.entry("png", "image/png"),
      Map.entry("svg", "image/svg+xml"),
      Map.entry("txt", "text/plain"),
      Map.entry("webp", "image/webp"),
      Map.entry("xml", "application/xml"),
      Map.entry("zip", "application/zip"));

  private AttachmentReferences() {
  }

  public static String normalizeTitle(String title) {
    if (title == null) {
      return null;
    }
    var stripped = title.strip();
    if (stripped.isEmpty()) {
      return null;
    }
    return stripped.toLowerCase(Locale.ROOT);
  }

  /** Classifies media as image by MIME/type then filename extension; unknown defaults to image. */
  public static boolean isImage(String mimeOrType, String fileName) {
    var classification = classify(mimeOrType);
    if (classification == null) {
      classification = classify(inferMediaType(fileName));
    }
    return classification == null || classification;
  }

  private static Boolean classify(String mimeOrType) {
    var normalized = mimeOrType == null ? null : mimeOrType.strip().toLowerCase(Locale.ROOT);
    if (normalized == null || normalized.isEmpty()) {
      return null;
    }
    if (normalized.equals("image") || normalized.startsWith("image/")) {
      return Boolean.TRUE;
    }
    return normalized.indexOf('/') >= 0 ? Boolean.FALSE : null;
  }

  public static AttachmentReference resolve(
      MacroParams macroParams,
      Map<String, AttachmentReference> attachmentReferencesByTitle) {
    if (macroParams == null) {
      return null;
    }

    var name = macroParams.value("name");
    var normalizedTitle = normalizeTitle(name);
    if (normalizedTitle != null && attachmentReferencesByTitle != null) {
      var resolved = attachmentReferencesByTitle.get(normalizedTitle);
      if (resolved != null) {
        return resolved;
      }
    }

    var fileId = Stream.of(
        macroParams.value("fileId"),
        macroParams.value("id"),
        macroParams.value("attachmentId"))
        .filter(s -> s != null && !s.isBlank())
        .findFirst()
        .orElse(null);
    if (fileId == null) {
      return null;
    }

    return new AttachmentReference(fileId, name, inferMediaType(name));
  }

  static String inferMediaType(String fileName) {
    if (fileName == null) {
      return null;
    }
    var normalizedName = fileName.strip();
    if (normalizedName.isEmpty()) {
      return null;
    }

    var guessed = URLConnection.guessContentTypeFromName(normalizedName);
    if (guessed != null && !guessed.isBlank()) {
      return guessed;
    }

    var dotIdx = normalizedName.lastIndexOf('.');
    if (dotIdx < 0) {
      return null;
    }
    var extension = normalizedName.substring(dotIdx + 1);
    if (extension.isBlank()) {
      return null;
    }

    return MEDIA_TYPES_BY_EXTENSION.get(extension.toLowerCase(Locale.ROOT));
  }
}
