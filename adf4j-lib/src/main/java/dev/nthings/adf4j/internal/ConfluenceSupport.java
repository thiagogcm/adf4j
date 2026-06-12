package dev.nthings.adf4j.internal;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.ast.MediaAttrs;
import dev.nthings.adf4j.confluence.ConfluenceMetadata;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.PageTreeMacro;
import dev.nthings.adf4j.metadata.PageTreeReference;

public final class ConfluenceSupport {

  private static final Pattern CONFLUENCE_PAGE_URL_PATTERN = Pattern.compile(
      "^(?:https?://[^/]+)?(?:/wiki)?(?:/spaces/[^/]+)?/pages/(?:edit-v\\d+/)?(\\d+)(?:/[^?#]*)?(?:\\?.*)?(?:#.*)?$",
      Pattern.CASE_INSENSITIVE);
  private static final String CONFLUENCE_MACRO_EXTENSION = "com.atlassian.confluence.macro.core";
  private static final String CHART_EXTENSION = "com.atlassian.chart";
  private static final String MIGRATION_EXTENSION = "com.atlassian.confluence.migration";

  private ConfluenceSupport() {
  }

  public static boolean isConfluenceMacroExtension(String extensionType) {
    return CONFLUENCE_MACRO_EXTENSION.equals(extensionType);
  }

  /**
   * Whether the extension is a chart: the modern chart app ({@code com.atlassian.chart}, any key) or
   * the legacy Confluence {@code chart}/{@code chart:*} macro.
   */
  public static boolean isChartExtension(String extensionType, String extensionKey) {
    if (isModernChartExtension(extensionType)) {
      return true;
    }
    return isConfluenceMacroExtension(extensionType)
        && extensionKey != null
        && ("chart".equals(extensionKey) || extensionKey.startsWith("chart:"));
  }

  /** Whether the extension belongs to the modern chart app ({@code com.atlassian.chart}). */
  public static boolean isModernChartExtension(String extensionType) {
    return CHART_EXTENSION.equals(extensionType);
  }

  /**
   * The user-visible chart title, or {@code null}. The legacy macro carries it as the {@code title}
   * macro param; the modern chart app nests it at
   * {@code parameters.chartGroup.customizeTab.titlesField.chartTitle} with the generic
   * {@code extensionTitle} as a fallback.
   */
  public static String chartTitle(MacroParams macroParams, Attributes parameters) {
    var fromParams = macroParams == null ? null : macroParams.value("title");
    var nested = parameters == null
        ? null
        : parameters.object("chartGroup").object("customizeTab").object("titlesField")
            .string("chartTitle");
    var generic = parameters == null ? null : parameters.string("extensionTitle");
    return Stream.of(fromParams, nested, generic)
        .map(s -> s == null ? null : s.strip())
        .filter(s -> s != null && !s.isEmpty())
        .findFirst()
        .orElse(null);
  }

  /** Whether the extension is the editor-migration {@code inline-media-image} macro. */
  public static boolean isInlineMediaImage(String extensionType, String extensionKey) {
    return MIGRATION_EXTENSION.equals(extensionType) && "inline-media-image".equals(extensionKey);
  }

  /**
   * The {@code media}-node attributes equivalent to an {@code inline-media-image} macro, or
   * {@code null} when the macro carries no media id. The migration macro stores the media identity
   * directly under {@code parameters} ({@code id}, {@code collection}, {@code width}, {@code height}),
   * not under {@code macroParams}; it always wraps an image, so the media type is fixed.
   */
  public static MediaAttrs inlineMediaImageAttrs(Attributes parameters) {
    if (parameters == null) {
      return null;
    }
    var id = stringValue(parameters, "id");
    if (id == null) {
      return null;
    }
    return MediaAttrs.builder()
        .type("file")
        .mediaType("image")
        .id(id)
        .collection(stringValue(parameters, "collection"))
        .alt(stringValue(parameters, "alt"))
        .width(stringValue(parameters, "width"))
        .height(stringValue(parameters, "height"))
        .build();
  }

  // A parameter as trimmed text (numbers stringified), or null when absent/blank.
  private static String stringValue(Attributes parameters, String key) {
    var value = parameters.values().get(key);
    var text = switch (value) {
      case null -> null;
      case String s -> s;
      case Number n -> n.toString();
      default -> null;
    };
    if (text == null) {
      return null;
    }
    var stripped = text.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  /**
   * The {@link ExcerptIncludeReference} for an {@code excerpt-include} macro, or {@code null} for any
   * other extension key or when the source page (the macro's unnamed default parameter) is absent.
   * The single place the macro's parameters are normalized, shared by rendering and metadata
   * extraction.
   */
  public static ExcerptIncludeReference excerptIncludeReference(
      String extensionKey, MacroParams macroParams) {
    if (!"excerpt-include".equals(extensionKey)) {
      return null;
    }
    var params = macroParams == null ? MacroParams.empty() : macroParams;
    var page = trimToNull(params.value(""));
    if (page == null) {
      return null;
    }
    return new ExcerptIncludeReference(page, trimToNull(params.value("name")), params.values());
  }

  /** The {@code excerpt} macro's named-excerpt identifier, or {@code null} for the unnamed excerpt. */
  public static String excerptName(MacroParams macroParams) {
    return macroParams == null ? null : trimToNull(macroParams.value("name"));
  }

  /** The stripped value, or {@code null} when it is null or blank. */
  public static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    var stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
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
   * The {@link PageTreeReference} for a {@code pagetree}/{@code children} macro, or {@code null} for
   * any other extension key. The single place the macro's root parameter ({@code root} for pagetree,
   * {@code page} for children) is normalized, shared by rendering and metadata extraction.
   */
  public static PageTreeReference pageTreeReference(String extensionKey, MacroParams macroParams) {
    var params = macroParams == null ? MacroParams.empty() : macroParams;
    return switch (extensionKey != null ? extensionKey : "") {
      case "pagetree" ->
          new PageTreeReference(PageTreeMacro.PAGETREE, rootParam(params, "root"), params.values());
      case "children" ->
          new PageTreeReference(PageTreeMacro.CHILDREN, rootParam(params, "page"), params.values());
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
