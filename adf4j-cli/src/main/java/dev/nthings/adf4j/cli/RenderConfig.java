package dev.nthings.adf4j.cli;

import static java.util.Objects.requireNonNullElse;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.PageTreeMacro;
import dev.nthings.adf4j.options.AttachmentResolver;
import dev.nthings.adf4j.options.ExcerptResolver;
import dev.nthings.adf4j.options.ExtensionRenderer;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.MediaResolver;
import dev.nthings.adf4j.options.PageLinkResolver;
import dev.nthings.adf4j.options.PageTreeEntry;
import dev.nthings.adf4j.options.PageTreeResolver;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Translates the shared rendering/resolver flags into a {@link MarkdownOptions}. Resolvers are
 * data-driven (URL templates plus JSON lookup tables) and preserve the library's decline-vs-answer
 * contract: an absent key/entry declines, a present entry answers (an empty value being a valid
 * empty answer). See {@code docs/usage-guide.md} for the data-file schemas.
 */
final class RenderConfig {

  private static final Set<String> MEDIA_PLACEHOLDERS = Set.of("id", "collection", "localId");
  private static final Set<String> ATTACHMENT_PLACEHOLDERS = Set.of("fileId", "title");
  private static final Set<String> PAGE_PLACEHOLDERS = Set.of("pageId");

  private RenderConfig() {}

  static MarkdownOptions build(Args args, CliJson json) {
    var builder = MarkdownOptions.builder();

    if (args.value("title") != null) {
      builder.documentTitle(args.value("title"));
    }
    builder.collapseHardBreaks(args.has("collapse-hard-breaks"));
    builder.escapeParentheses(args.has("escape-parentheses"));
    builder.imageSizeAttributes(args.has("image-size"));
    builder.htmlVisualMarks(args.has("html-visual-marks"));
    if (args.value("unknown-nodes") != null) {
      builder.unknownNodePolicy(unknownNodePolicy(args.value("unknown-nodes")));
    }
    if (args.value("table-fallback") != null) {
      builder.tableFallback(tableFallback(args.value("table-fallback")));
    }

    builder.mediaResolver(mediaResolver(args, json));
    builder.attachmentResolver(attachmentResolver(args, json));
    builder.pageLinkResolver(pageLinkResolver(args, json));
    builder.pageTreeResolver(pageTreeResolver(args, json));
    builder.excerptResolver(excerptResolver(args, json));
    var extension = extensionRenderer(args, json);
    if (extension != null) {
      builder.extensionRenderers(List.of(extension));
    }
    builder.confluenceContext(confluenceContext(args, json));

    return builder.build();
  }

  // ---- enum options -------------------------------------------------------

  private static UnknownNodePolicy unknownNodePolicy(String value) {
    return switch (value) {
      case "placeholder" -> UnknownNodePolicy.PLACEHOLDER;
      case "skip" -> UnknownNodePolicy.SKIP;
      case "fail" -> UnknownNodePolicy.FAIL;
      case "preserve-raw" -> UnknownNodePolicy.PRESERVE_RAW;
      default ->
          throw CliException.usage(
              "--unknown-nodes must be one of: placeholder, skip, fail, preserve-raw");
    };
  }

  private static TableFallback tableFallback(String value) {
    return switch (value) {
      case "gfm-promote-first-row" -> TableFallback.GFM_PROMOTE_FIRST_ROW;
      case "gfm-empty-header" -> TableFallback.GFM_EMPTY_HEADER;
      case "html" -> TableFallback.HTML;
      default ->
          throw CliException.usage(
              "--table-fallback must be one of: gfm-promote-first-row, gfm-empty-header, html");
    };
  }

  // ---- URL-rewriting resolvers (template + map, map wins) -----------------

  private static @Nullable MediaResolver mediaResolver(Args args, CliJson json) {
    var lookup = urlResolver(args, json, "media-map", "media-url", MEDIA_PLACEHOLDERS);
    return lookup == null
        ? null
        : attrs ->
            lookup.apply(
                attrs.id(),
                Map.of(
                    "id", requireNonNullElse(attrs.id(), ""),
                    "collection", requireNonNullElse(attrs.collection(), ""),
                    "localId", requireNonNullElse(attrs.localId(), "")));
  }

  private static @Nullable AttachmentResolver attachmentResolver(Args args, CliJson json) {
    var lookup =
        urlResolver(args, json, "attachment-map", "attachment-url", ATTACHMENT_PLACEHOLDERS);
    return lookup == null
        ? null
        : reference ->
            lookup.apply(
                reference.fileId(),
                Map.of(
                    "fileId", requireNonNullElse(reference.fileId(), ""),
                    "title", requireNonNullElse(reference.title(), "")));
  }

  private static @Nullable PageLinkResolver pageLinkResolver(Args args, CliJson json) {
    var lookup = urlResolver(args, json, "page-map", "page-url", PAGE_PLACEHOLDERS);
    return lookup == null
        ? null
        : pageNodeId ->
            lookup.apply(pageNodeId, Map.of("pageId", requireNonNullElse(pageNodeId, "")));
  }

  // (key, placeholders) -> URL: the map wins on a non-blank hit, else the template expands; null
  // when
  // neither flag was given (so the resolver stays unset and the library keeps its default
  // behavior).
  private static @Nullable BiFunction<String, Map<String, String>, @Nullable String> urlResolver(
      Args args, CliJson json, String mapFlag, String urlFlag, Set<String> placeholders) {
    var map = readStringMap(args.value(mapFlag), json, "--" + mapFlag);
    var template = template(args.value(urlFlag), placeholders, "--" + urlFlag);
    if (map.isEmpty() && template == null) {
      return null;
    }
    return (key, values) -> {
      var hit = map.get(key);
      if (hit != null && !hit.isBlank()) {
        return hit;
      }
      return template == null ? null : template.expand(values);
    };
  }

  // ---- structured-data resolvers ------------------------------------------

  private static @Nullable PageTreeResolver pageTreeResolver(Args args, CliJson json) {
    var path = args.value("page-tree-map");
    if (path == null) {
      return null;
    }
    var root =
        CliJson.requireObject(json.readFile(Path.of(path), "--page-tree-map"), "--page-tree-map");
    CliJson.rejectUnknownKeys(root, Set.of("pagetree", "children"), "--page-tree-map");
    var byMacro = new LinkedHashMap<PageTreeMacro, Map<String, List<PageTreeEntry>>>();
    byMacro.put(PageTreeMacro.PAGETREE, readPageTreeBucket(root.get("pagetree"), "pagetree"));
    byMacro.put(PageTreeMacro.CHILDREN, readPageTreeBucket(root.get("children"), "children"));

    return reference -> {
      var bucket = byMacro.getOrDefault(reference.macro(), Map.of());
      var key = reference.root() == null ? "" : reference.root();
      return bucket.get(key); // absent key -> null (decline); present (even empty) -> authoritative
    };
  }

  private static Map<String, List<PageTreeEntry>> readPageTreeBucket(
      @Nullable JsonNode bucketNode, String name) {
    var bucket = new LinkedHashMap<String, List<PageTreeEntry>>();
    if (bucketNode == null || bucketNode.isNull()) {
      return bucket;
    }
    var object = CliJson.requireObject(bucketNode, "--page-tree-map '" + name + "'");
    for (var entry : object.properties()) {
      var entries = new ArrayList<PageTreeEntry>();
      var array =
          CliJson.requireArray(
              entry.getValue(), "--page-tree-map '" + name + "/" + entry.getKey() + "'");
      for (var item : array) {
        entries.add(
            new PageTreeEntry(
                item.path("depth").asInt(),
                requireNonNullElse(CliJson.string(item, "title"), ""),
                CliJson.string(item, "pageNodeId")));
      }
      bucket.put(entry.getKey(), List.copyOf(entries));
    }
    return bucket;
  }

  private static @Nullable ExcerptResolver excerptResolver(Args args, CliJson json) {
    var path = args.value("excerpt-map");
    if (path == null) {
      return null;
    }
    var array =
        CliJson.requireArray(json.readFile(Path.of(path), "--excerpt-map"), "--excerpt-map");
    var byKey = new LinkedHashMap<ExcerptKey, String>();
    for (var item : array) {
      var object = CliJson.requireObject(item, "--excerpt-map entry");
      CliJson.rejectUnknownKeys(object, Set.of("page", "name", "markdown"), "--excerpt-map entry");
      var page = CliJson.requireString(object, "page", "--excerpt-map entry");
      var excerptName = CliJson.string(object, "name"); // null == the unnamed excerpt
      byKey.put(
          new ExcerptKey(page, excerptName),
          requireNonNullElse(CliJson.string(object, "markdown"), ""));
    }
    return reference -> byKey.get(new ExcerptKey(reference.page(), reference.excerptName()));
  }

  private static @Nullable ExtensionRenderer extensionRenderer(Args args, CliJson json) {
    var path = args.value("extension-map");
    if (path == null) {
      return null;
    }
    var array =
        CliJson.requireArray(json.readFile(Path.of(path), "--extension-map"), "--extension-map");
    var entries = new ArrayList<ExtensionEntry>();
    for (var item : array) {
      var object = CliJson.requireObject(item, "--extension-map entry");
      CliJson.rejectUnknownKeys(object, Set.of("type", "key", "template"), "--extension-map entry");
      entries.add(
          new ExtensionEntry(
              CliJson.string(object, "type"),
              CliJson.requireString(object, "key", "--extension-map entry"),
              requireNonNullElse(CliJson.string(object, "template"), "")));
    }
    return context -> {
      for (var entry : entries) {
        if (entry.key().equals(context.extensionKey())
            && (entry.type() == null || entry.type().equals(context.extensionType()))) {
          // Verbatim per the ExtensionRenderer contract; unknown {tokens} become empty.
          return UrlTemplate.render(
              entry.template(), name -> requireNonNullElse(context.parameter(name), ""));
        }
      }
      return null; // no match -> defer to built-in macros
    };
  }

  private static ConfluenceRenderContext confluenceContext(Args args, CliJson json) {
    var path = args.value("attachments-map");
    if (path == null) {
      return ConfluenceRenderContext.empty();
    }
    var array =
        CliJson.requireArray(
            json.readFile(Path.of(path), "--attachments-map"), "--attachments-map");
    var references = new ArrayList<AttachmentReference>();
    for (var item : array) {
      var object = CliJson.requireObject(item, "--attachments-map entry");
      CliJson.rejectUnknownKeys(
          object, Set.of("fileId", "title", "mediaType"), "--attachments-map entry");
      references.add(
          new AttachmentReference(
              CliJson.requireString(object, "fileId", "--attachments-map entry"),
              CliJson.string(object, "title"),
              CliJson.string(object, "mediaType")));
    }
    // A supplied inventory (even empty) is authoritative: attachmentsSupplied becomes true.
    return ConfluenceRenderContext.empty().withAttachmentReferences(references);
  }

  // ---- helpers ------------------------------------------------------------

  private static @Nullable UrlTemplate template(
      @Nullable String value, Set<String> allowed, String flag) {
    return value == null ? null : new UrlTemplate(value, allowed, flag);
  }

  private static Map<String, String> readStringMap(
      @Nullable String path, CliJson json, String flag) {
    if (path == null) {
      return Map.of();
    }
    var object = CliJson.requireObject(json.readFile(Path.of(path), flag), flag);
    var map = new LinkedHashMap<String, String>();
    for (var entry : object.properties()) {
      map.put(entry.getKey(), entry.getValue().isNull() ? "" : entry.getValue().asString());
    }
    return map;
  }

  private record ExcerptKey(String page, @Nullable String name) {}

  private record ExtensionEntry(@Nullable String type, String key, String template) {}
}
