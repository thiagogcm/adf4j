package dev.nthings.adf4j;

import java.net.URLConnection;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import dev.nthings.adf4j.ast.MacroParams;

import tools.jackson.databind.JsonNode;

public final class AdfJson {

  private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\\R");
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
  private static final Pattern CONFLUENCE_PAGE_URL_PATTERN = Pattern.compile(
      "^(?:https?://[^/]+)?(?:/wiki)?(?:/spaces/[^/]+)?/pages/(?:edit-v\\d+/)?(\\d+)(?:/[^?#]*)?(?:\\?.*)?(?:#.*)?$",
      Pattern.CASE_INSENSITIVE);
  private static final String CONFLUENCE_MACRO_EXTENSION = "com.atlassian.confluence.macro.core";

  private AdfJson() {
  }

  // ─────────────────────────── JsonNode helpers (parser-only) ───────────────────────────

  public static String text(JsonNode node, String field) {
    return text(node, field, null);
  }

  public static String text(JsonNode node, String field, String fallback) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return fallback;
    }

    var value = node.get(field);
    if (value == null || value.isNull() || value.isMissingNode()) {
      return fallback;
    }

    return value.asString();
  }

  // ─────────────────────────────── String helpers ───────────────────────────────

  public static int clampHeadingLevel(int level) {
    return Math.clamp(level, 1, 6);
  }

  public static String dateFromTimestamp(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return "";
    }

    try {
      var value = Long.parseLong(timestamp);
      var date = Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate();
      return date.toString();
    } catch (NumberFormatException _) {
      return timestamp;
    }
  }

  public static boolean isConfluenceMacroExtension(String extensionType) {
    return CONFLUENCE_MACRO_EXTENSION.equals(extensionType);
  }

  public static String confluencePageId(String rawUrl) {
    if (rawUrl == null) {
      return null;
    }
    var normalizedUrl = rawUrl.strip();
    if (normalizedUrl.isEmpty()) {
      return null;
    }

    var matcher = CONFLUENCE_PAGE_URL_PATTERN.matcher(normalizedUrl);
    return matcher.matches() ? matcher.group(1) : null;
  }

  public static List<String> splitLines(String value) {
    if (value == null) {
      return List.of();
    }
    return Arrays.asList(LINE_BREAK_PATTERN.split(value, -1));
  }

  public static String escapeMarkdownLinkText(String value) {
    return Objects.requireNonNullElse(value, "").replace("[", "\\[").replace("]", "\\]");
  }

  // ─────────────────────────── Typed macro helpers ───────────────────────────

  public static String anchorId(MacroParams macroParams) {
    if (macroParams == null) {
      return null;
    }
    return Stream.of(macroParams.value(""), macroParams.value("legacyAnchorId"))
        .map(s -> s == null ? null : s.strip())
        .filter(s -> s != null && !s.isEmpty())
        .findFirst()
        .orElse(null);
  }

  public static AttachmentReference resolveAttachmentReference(
      MacroParams macroParams,
      Map<String, AttachmentReference> attachmentReferencesByTitle) {
    if (macroParams == null) {
      return null;
    }

    var name = macroParams.value("name");
    var normalizedTitle = RenderOptions.normalizeAttachmentTitle(name);
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

  private static String inferMediaType(String fileName) {
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
