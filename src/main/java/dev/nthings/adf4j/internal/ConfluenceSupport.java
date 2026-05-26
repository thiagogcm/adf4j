package dev.nthings.adf4j.internal;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import dev.nthings.adf4j.ast.MacroParams;

public final class ConfluenceSupport {

  private static final Pattern CONFLUENCE_PAGE_URL_PATTERN = Pattern.compile(
      "^(?:https?://[^/]+)?(?:/wiki)?(?:/spaces/[^/]+)?/pages/(?:edit-v\\d+/)?(\\d+)(?:/[^?#]*)?(?:\\?.*)?(?:#.*)?$",
      Pattern.CASE_INSENSITIVE);
  private static final String CONFLUENCE_MACRO_EXTENSION = "com.atlassian.confluence.macro.core";

  private ConfluenceSupport() {
  }

  public static boolean isConfluenceMacroExtension(String extensionType) {
    return CONFLUENCE_MACRO_EXTENSION.equals(extensionType);
  }

  public static String pageId(String rawUrl) {
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
}
