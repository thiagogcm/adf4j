package dev.nthings.adf4j.internal;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.confluence.ConfluenceMetadata;
import dev.nthings.adf4j.options.PageTreeMacro;
import dev.nthings.adf4j.options.PageTreeRequest;

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

  /**
   * The Confluence page node id a link points at, or {@code null} when the link is not an internal
   * page reference. Combines the id inferred from a page URL with the {@code linkType}/id carried in
   * the node's Confluence metadata: a reference counts as a page when the URL parses to a page id, the
   * metadata carries a page id, or the metadata's {@code linkType} is {@code "page"}. The single
   * source of truth shared by metadata extraction and {@code PageLinkResolver} rewriting, so both
   * agree on which links are pages.
   */
  public static String pageNodeId(String url, ConfluenceMetadata metadata) {
    var normalizedUrl = url == null ? null : url.strip();
    if (normalizedUrl == null || normalizedUrl.isEmpty() || "#".equals(normalizedUrl)) {
      return null;
    }

    var inferredNodeId = pageId(normalizedUrl);
    var metadataNodeId = metadata == null
        ? null
        : Stream.of(metadata.pageId(), metadata.contentId(), metadata.id())
            .filter(value -> value != null && !value.isBlank())
            .map(String::strip)
            .findFirst()
            .orElse(null);
    var linkType = metadata == null || metadata.linkType() == null ? null : metadata.linkType().strip();
    if (!"page".equalsIgnoreCase(linkType)
        && inferredNodeId == null
        && metadataNodeId == null) {
      return null;
    }
    return inferredNodeId != null ? inferredNodeId : metadataNodeId;
  }

  /**
   * The {@link PageTreeRequest} for a {@code pagetree}/{@code children} macro, or {@code null} for any
   * other extension key. The single place the macro's root parameter ({@code root} for pagetree,
   * {@code page} for children) is normalized, shared by rendering and metadata extraction.
   */
  public static PageTreeRequest pageTreeRequest(String extensionKey, MacroParams macroParams) {
    var params = macroParams == null ? MacroParams.empty() : macroParams;
    return switch (extensionKey != null ? extensionKey : "") {
      case "pagetree" ->
          new PageTreeRequest(PageTreeMacro.PAGETREE, rootParam(params, "root"), params.values());
      case "children" ->
          new PageTreeRequest(PageTreeMacro.CHILDREN, rootParam(params, "page"), params.values());
      default -> null;
    };
  }

  // A macro root parameter (trimmed), or null for a blank or "@keyword" root.
  private static String rootParam(MacroParams macroParams, String name) {
    var value = macroParams.value(name);
    if (value == null) {
      return null;
    }
    var trimmed = value.strip();
    return trimmed.isEmpty() || trimmed.startsWith("@") ? null : trimmed;
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
