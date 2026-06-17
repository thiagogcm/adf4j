package dev.nthings.adf4j.internal;

import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.metadata.AttachmentReference;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
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

  /// Classifies media by MIME/type, falling back to each name/URL extension; unknown is non-image.
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

  public static @Nullable AttachmentReference resolve(
      @Nullable MacroParams macroParams, @Nullable ConfluenceRenderContext confluenceContext) {
    if (macroParams == null) {
      return null;
    }

    var name = macroParams.value("name");
    if (confluenceContext != null) {
      var resolved = confluenceContext.attachment(name);
      if (resolved != null) {
        return resolved;
      }
    }

    var fileId =
        Stream.of(
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

  /// The file name a URL or path carries: its last path segment, or null when none is present.
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
    // A URL's query/fragment must not corrupt (or spuriously supply) the extension.
    var name = lastPathSegment(fileNameOrUrl.strip());
    if (name.isEmpty()) {
      return null;
    }

    var guessed = URLConnection.guessContentTypeFromName(name);
    if (guessed != null && !guessed.isBlank()) {
      return guessed;
    }

    var dotIdx = name.lastIndexOf('.');
    if (dotIdx < 0) {
      return null;
    }
    var extension = name.substring(dotIdx + 1);
    if (extension.isBlank()) {
      return null;
    }

    return MEDIA_TYPES_BY_EXTENSION.get(extension.toLowerCase(Locale.ROOT));
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
