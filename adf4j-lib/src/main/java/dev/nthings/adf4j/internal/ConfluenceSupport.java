package dev.nthings.adf4j.internal;

import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.MacroParams;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Confluence heuristics not tied to one macro: page ids in links, and where a chart keeps its
/// title.
public final class ConfluenceSupport {

  private static final Pattern CONFLUENCE_PAGE_URL_PATTERN =
      Pattern.compile(
          "^(?:https?://[^/]+)?(?:/wiki)?(?:/spaces/[^/]+)?/pages/(?:edit-v\\d+/)?(\\d+)(?:/[^?#]*)?(?:\\?.*)?(?:#.*)?$",
          Pattern.CASE_INSENSITIVE);

  private ConfluenceSupport() {}

  /// Legacy charts keep the title in the `title` macro param. Modern charts nest it at
  /// `parameters.chartGroup.customizeTab.titlesField.chartTitle`, then `extensionTitle`.
  public static @Nullable String chartTitle(MacroParams macroParams, Attributes parameters) {
    var nested =
        parameters
            .object("chartGroup")
            .object("customizeTab")
            .object("titlesField")
            .string("chartTitle");
    return Strings.firstNonBlankStripped(
        macroParams.value("title"), nested, parameters.string("extensionTitle"));
  }

  public static @Nullable String pageId(String rawUrl) {
    var matcher = CONFLUENCE_PAGE_URL_PATTERN.matcher(rawUrl.strip());
    return matcher.matches() ? matcher.group(1) : null;
  }

  /// Page node id a link points at, or `null` for a non-page link. The URL id wins over metadata.
  /// Shared with `PageLinkResolver` so both agree on which links are pages.
  public static @Nullable String pageNodeId(String url, ConfluenceMetadata metadata) {
    var normalizedUrl = url.strip();
    if (normalizedUrl.isEmpty() || "#".equals(normalizedUrl)) {
      return null;
    }

    var inferredNodeId = pageId(normalizedUrl);
    if (inferredNodeId != null) {
      return inferredNodeId;
    }
    return Strings.firstNonBlankStripped(metadata.pageId(), metadata.contentId(), metadata.id());
  }
}
