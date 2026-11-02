package dev.nthings.adf4j.internal;

import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.MacroParams;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Confluence heuristics that aren't tied to one macro: page ids in URLs and link metadata, and
/// where a chart keeps its title.
public final class ConfluenceSupport {

  private static final Pattern CONFLUENCE_PAGE_URL_PATTERN =
      Pattern.compile(
          "^(?:https?://[^/]+)?(?:/wiki)?(?:/spaces/[^/]+)?/pages/(?:edit-v\\d+/)?(\\d+)(?:/[^?#]*)?(?:\\?.*)?(?:#.*)?$",
          Pattern.CASE_INSENSITIVE);

  private ConfluenceSupport() {}

  /// The user-visible chart title, or `null`. The legacy macro carries it as the `title`
  /// macro param; the modern chart app nests it at
  /// `parameters.chartGroup.customizeTab.titlesField.chartTitle` with the generic
  /// `extensionTitle` as a fallback.
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

  /// The Confluence page node id a link points at, or `null` when the link is not an internal
  /// page reference. A link counts as a page when its URL parses to a page id or its Confluence
  /// metadata carries one; the inferred URL id wins over the metadata id. The single source of
  /// truth shared by metadata extraction and `PageLinkResolver` rewriting, so both agree on which
  /// links are pages.
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
