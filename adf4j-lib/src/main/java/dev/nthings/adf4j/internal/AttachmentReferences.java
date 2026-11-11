package dev.nthings.adf4j.internal;

import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.metadata.AttachmentReference;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class AttachmentReferences {

  private static final Map<String, String> MEDIA_TYPES_BY_EXTENSION =
      Map.ofEntries(
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

  private AttachmentReferences() {}

  /// Classifies by MIME or type, then by each name's extension. Unknown counts as non-image.
  public static boolean isImage(@Nullable String mimeOrType, @Nullable String... fileNames) {
    var classification = classify(mimeOrType);
    for (var fileName : fileNames) {
      if (classification != null) {
        break;
      }
      classification = classify(inferMediaType(fileName));
    }
    return classification != null && classification;
  }

  private static @Nullable Boolean classify(@Nullable String mimeOrType) {
    var normalized = mimeOrType == null ? null : mimeOrType.strip().toLowerCase(Locale.ROOT);
    if (normalized == null || normalized.isEmpty()) {
      return null;
    }
    if (normalized.equals("image") || normalized.startsWith("image/")) {
      return Boolean.TRUE;
    }
    return normalized.indexOf('/') >= 0 ? Boolean.FALSE : null;
  }

  /// Resolves by file id first (titles can collide), then by title, else from the macro itself.
  /// A title-less entry takes the macro's name.
  public static @Nullable AttachmentReference resolve(
      MacroParams macroParams, AttachmentInventory attachments) {
    var name = macroParams.value("name");
    var fileId =
        Strings.firstNonBlank(
            macroParams.value("fileId"),
            macroParams.value("id"),
            macroParams.value("attachmentId"));

    var entry = attachments.byFileId(fileId);
    if (entry == null) {
      entry = attachments.byTitle(name);
    }
    if (entry == null) {
      return fileId == null ? null : new AttachmentReference(fileId, name, inferMediaType(name));
    }
    var title = entry.title();
    return title != null && !title.isBlank()
        ? entry
        : new AttachmentReference(
            entry.fileId(),
            name,
            Strings.firstNonBlank(entry.mediaType(), inferMediaType(name)),
            entry.downloadUrl());
  }

  public static @Nullable String fileName(@Nullable String urlOrPath) {
    if (urlOrPath == null) {
      return null;
    }
    var name = lastPathSegment(urlOrPath.strip());
    return name.isEmpty() ? null : name;
  }

  public static @Nullable String inferMediaType(@Nullable String fileNameOrUrl) {
    if (fileNameOrUrl == null) {
      return null;
    }
    // Query and fragment must not supply or corrupt the extension.
    var name = lastPathSegment(fileNameOrUrl.strip());
    if (name.isEmpty()) {
      return null;
    }

    // Curated map first: URLConnection's answer depends on the JVM's content types.
    var dotIdx = name.lastIndexOf('.');
    if (dotIdx >= 0) {
      var extension = name.substring(dotIdx + 1);
      if (!extension.isBlank()) {
        var known = MEDIA_TYPES_BY_EXTENSION.get(extension.toLowerCase(Locale.ROOT));
        if (known != null) {
          return known;
        }
      }
    }

    var guessed = URLConnection.guessContentTypeFromName(name);
    return guessed == null || guessed.isBlank() ? null : guessed;
  }

  private static String lastPathSegment(String value) {
    var end = value.length();
    var hash = value.indexOf('#');
    if (hash >= 0) {
      end = hash;
    }
    var query = value.indexOf('?');
    if (query >= 0 && query < end) {
      end = query;
    }
    var slash = value.lastIndexOf('/', end - 1);
    return value.substring(slash + 1, end);
  }
}
