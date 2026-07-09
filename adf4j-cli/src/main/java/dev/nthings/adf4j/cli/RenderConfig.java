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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/// Translates the shared rendering/resolver flags into a {@link MarkdownOptions}. Resolvers are
/// data-driven (URL templates plus JSON lookup tables) and preserve the library's decline-vs-answer
/// contract: an absent key/entry declines, a present entry answers (an empty value being a valid
/// empty answer).
final class RenderConfig {

  private static final Set<String> MEDIA_PLACEHOLDERS = Set.of("id", "collection", "localId");
  private static final Set<String> ATTACHMENT_PLACEHOLDERS = Set.of("fileId", "title");
  private static final Set<String> PAGE_PLACEHOLDERS = Set.of("pageId");

  private RenderConfig() {}

  static MarkdownOptions build(
      RenderingOptions rendering, ResolverOptions resolvers, CliJson json) {
    var builder = MarkdownOptions.builder();

    if (rendering.title != null) {
      builder.documentTitle(rendering.title);
    }
    builder.collapseHardBreaks(rendering.collapseHardBreaks);
    builder.escapeParentheses(rendering.escapeParentheses);
    builder.imageSizeAttributes(rendering.imageSize);
    builder.htmlVisualMarks(rendering.htmlVisualMarks);
    if (rendering.unknownNodes != null) {
      builder.unknownNodePolicy(enumOption(UnknownNodePolicy.values(), rendering.unknownNodes));
    }
    if (rendering.tableFallback != null) {
      builder.tableFallback(enumOption(TableFallback.values(), rendering.tableFallback));
    }

    builder.mediaResolver(mediaResolver(resolvers, json));
    builder.attachmentResolver(attachmentResolver(resolvers, json));
    builder.pageLinkResolver(pageLinkResolver(resolvers, json));
    builder.pageTreeResolver(pageTreeResolver(resolvers, json));
    builder.excerptResolver(excerptResolver(resolvers, json));
    var extension = extensionRenderer(resolvers, json);
    if (extension != null) {
      builder.extensionRenderers(List.of(extension));
    }
    builder.confluenceContext(confluenceContext(resolvers, json));

    return builder.build();
  }

  // ---- enum options -------------------------------------------------------

  // The CLI spelling of an enum option is the kebab-case of its constant name. Takes the
  // constants (not the Class) so the native/wasm images stay reflection-free. The parser already
  // validated the value against the option's allowedValues, so a miss here is a bug (the
  // annotation list drifted from the enum; OptionContractTest pins them together).
  private static <E extends Enum<E>> E enumOption(E[] constants, String value) {
    for (var constant : constants) {
      if (kebabCase(constant).equals(value)) {
        return constant;
      }
    }
    throw new IllegalStateException("option value '" + value + "' missing from the enum");
  }

  static String kebabCase(Enum<?> constant) {
    return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  // ---- URL-rewriting resolvers (template + map, map wins) -----------------

  private static @Nullable MediaResolver mediaResolver(ResolverOptions resolvers, CliJson json) {
    var lookup =
        urlResolver(
            resolvers.mediaMap,
            resolvers.mediaUrl,
            json,
            "--media-map",
            "--media-url",
            MEDIA_PLACEHOLDERS);
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

  private static @Nullable AttachmentResolver attachmentResolver(
      ResolverOptions resolvers, CliJson json) {
    var lookup =
        urlResolver(
            resolvers.attachmentMap,
            resolvers.attachmentUrl,
            json,
            "--attachment-map",
            "--attachment-url",
            ATTACHMENT_PLACEHOLDERS);
    return lookup == null
        ? null
        : reference ->
            lookup.apply(
                reference.fileId(),
                Map.of(
                    "fileId", requireNonNullElse(reference.fileId(), ""),
                    "title", requireNonNullElse(reference.title(), "")));
  }

  private static @Nullable PageLinkResolver pageLinkResolver(
      ResolverOptions resolvers, CliJson json) {
    var lookup =
        urlResolver(
            resolvers.pageMap,
            resolvers.pageUrl,
            json,
            "--page-map",
            "--page-url",
            PAGE_PLACEHOLDERS);
    return lookup == null
        ? null
        : pageNodeId ->
            lookup.apply(pageNodeId, Map.of("pageId", requireNonNullElse(pageNodeId, "")));
  }

  // (key, placeholders) -> URL: the map wins on a non-blank hit, else the template expands; null
  // when neither flag was given (so the resolver stays unset and the library keeps its default
  // behavior).
  private static @Nullable BiFunction<String, Map<String, String>, @Nullable String> urlResolver(
      @Nullable String mapPath,
      @Nullable String urlTemplate,
      CliJson json,
      String mapFlag,
      String urlFlag,
      Set<String> placeholders) {
    var map = readStringMap(mapPath, json, mapFlag);
    var template = template(urlTemplate, placeholders, urlFlag);
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

  private static @Nullable PageTreeResolver pageTreeResolver(
      ResolverOptions resolvers, CliJson json) {
    var path = resolvers.pageTreeMap;
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

  private static @Nullable ExcerptResolver excerptResolver(
      ResolverOptions resolvers, CliJson json) {
    var path = resolvers.excerptMap;
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

  private static @Nullable ExtensionRenderer extensionRenderer(
      ResolverOptions resolvers, CliJson json) {
    var path = resolvers.extensionMap;
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

  private static ConfluenceRenderContext confluenceContext(
      ResolverOptions resolvers, CliJson json) {
    var path = resolvers.attachmentsMap;
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
          object, Set.of("fileId", "title", "mediaType", "downloadUrl"), "--attachments-map entry");
      references.add(
          new AttachmentReference(
              CliJson.requireString(object, "fileId", "--attachments-map entry"),
              CliJson.string(object, "title"),
              CliJson.string(object, "mediaType"),
              CliJson.string(object, "downloadUrl")));
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
