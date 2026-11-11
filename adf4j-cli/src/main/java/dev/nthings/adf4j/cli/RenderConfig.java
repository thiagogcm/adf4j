package dev.nthings.adf4j.cli;

import static java.util.Objects.requireNonNullElse;

import dev.nthings.adf4j.metadata.PageTreeMacro;
import dev.nthings.adf4j.options.AttachmentResolver;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.ExcerptResolver;
import dev.nthings.adf4j.options.ExtensionOutput;
import dev.nthings.adf4j.options.ExtensionRenderer;
import dev.nthings.adf4j.options.MediaResolver;
import dev.nthings.adf4j.options.PageLinkResolver;
import dev.nthings.adf4j.options.PageTreeEntry;
import dev.nthings.adf4j.options.PageTreeResolver;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.AdfException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/// Turns the rendering and resolver flags of `convert` into a {@link ConversionOptions}. Resolvers
/// keep the library's decline-vs-answer contract: an absent key declines; a present entry answers,
/// even with an empty value.
final class RenderConfig {

  private static final Set<String> MEDIA_PLACEHOLDERS = Set.of("id", "collection", "localId");
  private static final Set<String> ATTACHMENT_PLACEHOLDERS = Set.of("fileId", "title");
  private static final Set<String> PAGE_PLACEHOLDERS = Set.of("pageId");
  private static final Set<String> PAGE_TREE_KEYS = Set.of("depth", "title", "pageNodeId");
  private static final Set<String> EXCERPT_KEYS = Set.of("page", "name", "markdown");
  private static final Set<String> EXTENSION_KEYS = Set.of("type", "key", "template");

  private RenderConfig() {}

  static ConversionOptions build(
      RenderingOptions rendering, ResolverOptions resolvers, CliJson json) {
    var base = ConversionOptions.defaults();
    if (rendering.config != null) {
      var raw = CliIo.read(Path.of(rendering.config), "config file");
      try {
        base = ConversionOptions.fromJson(raw);
      } catch (AdfException invalid) {
        throw CliException.usage("--config: " + invalid.getMessage());
      }
    }
    if (rendering.title != null) {
      base = base.withDocumentTitle(rendering.title);
    }
    if (rendering.collapseHardBreaks) {
      base = base.withCollapseHardBreaks(true);
    }
    if (rendering.escapeParentheses) {
      base = base.withEscapeParentheses(true);
    }
    if (rendering.imageSize) {
      base = base.withImageSizeAttributes(true);
    }
    if (rendering.htmlVisualMarks) {
      base = base.withHtmlVisualMarks(true);
    }
    var options =
        base.withMediaResolver(mediaResolver(resolvers, json))
            .withAttachmentResolver(attachmentResolver(resolvers, json))
            .withPageLinkResolver(pageLinkResolver(resolvers, json))
            .withPageTreeResolver(pageTreeResolver(resolvers, json))
            .withExcerptResolver(excerptResolver(resolvers, json))
            .withExtensionRenderers(extensionRenderers(resolvers, json));
    if (rendering.unknownNodes != null) {
      options =
          options.withUnknownNodePolicy(
              enumOption(UnknownNodePolicy.values(), rendering.unknownNodes));
    }
    if (rendering.tableFallback != null) {
      options =
          options.withTableFallback(enumOption(TableFallback.values(), rendering.tableFallback));
    }
    return options;
  }

  // Takes constants, not the Class, to stay reflection-free. The parser already checked
  // allowedValues, so a miss means an option's allowedValues drifted from its enum.
  private static <E extends Enum<E>> E enumOption(E[] constants, String value) {
    for (var constant : constants) {
      if (CliJson.kebabCase(constant).equals(value)) {
        return constant;
      }
    }
    throw new IllegalStateException("option value '" + value + "' missing from the enum");
  }

  private static @Nullable MediaResolver mediaResolver(ResolverOptions resolvers, CliJson json) {
    var lookup =
        new UrlLookup(
            readStringMap(resolvers.mediaMap, json, "--media-map"),
            template(resolvers.mediaUrl, MEDIA_PLACEHOLDERS, "--media-url"));
    return lookup.isUnset()
        ? null
        : media ->
            lookup.resolve(
                media.id() == null || media.id().isBlank() ? media.localId() : media.id(),
                Map.of(
                    "id", requireNonNullElse(media.id(), ""),
                    "collection", requireNonNullElse(media.collection(), ""),
                    "localId", requireNonNullElse(media.localId(), "")));
  }

  private static @Nullable AttachmentResolver attachmentResolver(
      ResolverOptions resolvers, CliJson json) {
    var lookup =
        new UrlLookup(
            readStringMap(resolvers.attachmentMap, json, "--attachment-map"),
            template(resolvers.attachmentUrl, ATTACHMENT_PLACEHOLDERS, "--attachment-url"));
    return lookup.isUnset()
        ? null
        : reference ->
            lookup.resolve(
                reference.fileId(),
                Map.of(
                    "fileId", reference.fileId(),
                    "title", requireNonNullElse(reference.title(), "")));
  }

  private static @Nullable PageLinkResolver pageLinkResolver(
      ResolverOptions resolvers, CliJson json) {
    var lookup =
        new UrlLookup(
            readStringMap(resolvers.pageMap, json, "--page-map"),
            template(resolvers.pageUrl, PAGE_PLACEHOLDERS, "--page-url"));
    return lookup.isUnset()
        ? null
        : pageNodeId -> lookup.resolve(pageNodeId, Map.of("pageId", pageNodeId));
  }

  // Unset when neither flag is given: the resolver stays null (library default).
  private record UrlLookup(Map<String, String> map, @Nullable UrlTemplate template) {

    boolean isUnset() {
      return map.isEmpty() && template == null;
    }

    // Map wins on a non-blank hit, else the template expands. No key declines, so an id-less node
    // keeps the library placeholder instead of an empty URL segment.
    @Nullable String resolve(@Nullable String key, Map<String, String> values) {
      if (key == null || key.isBlank()) {
        return null;
      }
      var hit = map.get(key);
      if (hit != null && !hit.isBlank()) {
        return hit;
      }
      return template == null ? null : template.expand(values);
    }
  }

  private static @Nullable PageTreeResolver pageTreeResolver(
      ResolverOptions resolvers, CliJson json) {
    var path = resolvers.pageTreeMap;
    if (path == null) {
      return null;
    }
    var flag = "--page-tree-map";
    var root = CliJson.requireObject(json.readFile(Path.of(path), flag), flag);
    var macros = PageTreeMacro.values();
    CliJson.rejectUnknownKeys(
        root, Arrays.stream(macros).map(CliJson::kebabCase).collect(Collectors.toSet()), flag);
    var byMacro = new LinkedHashMap<PageTreeMacro, Map<String, List<PageTreeEntry>>>();
    for (var macro : macros) {
      var name = CliJson.kebabCase(macro);
      byMacro.put(macro, readPageTreeBucket(root.get(name), flag + " '" + name + "'"));
    }

    return reference -> {
      var key = reference.root() == null ? "" : reference.root();
      // Absent key declines (null); a present bucket, even empty, answers.
      return byMacro.getOrDefault(reference.macro(), Map.of()).get(key);
    };
  }

  private static Map<String, List<PageTreeEntry>> readPageTreeBucket(
      @Nullable JsonNode bucketNode, String context) {
    var bucket = new LinkedHashMap<String, List<PageTreeEntry>>();
    if (bucketNode == null || bucketNode.isNull()) {
      return bucket;
    }
    for (var root : CliJson.requireObject(bucketNode, context).properties()) {
      var rootContext = context + " root '" + root.getKey() + "'";
      var entryContext = rootContext + " entry";
      var entries = new ArrayList<PageTreeEntry>();
      for (var item : CliJson.entries(root.getValue(), rootContext, PAGE_TREE_KEYS)) {
        entries.add(
            new PageTreeEntry(
                CliJson.integer(item, "depth", entryContext),
                CliJson.string(item, "title", entryContext),
                CliJson.string(item, "pageNodeId", entryContext)));
      }
      bucket.put(root.getKey(), List.copyOf(entries));
    }
    return bucket;
  }

  private static @Nullable ExcerptResolver excerptResolver(
      ResolverOptions resolvers, CliJson json) {
    var path = resolvers.excerptMap;
    if (path == null) {
      return null;
    }
    var flag = "--excerpt-map";
    var context = flag + " entry";
    var byKey = new LinkedHashMap<ExcerptKey, String>();
    for (var entry : json.readEntries(path, flag, EXCERPT_KEYS)) {
      var page = CliJson.requireString(entry, "page", context);
      var excerptName = CliJson.string(entry, "name", context); // null: the unnamed excerpt
      byKey.put(
          new ExcerptKey(page, excerptName),
          requireNonNullElse(CliJson.string(entry, "markdown", context), ""));
    }
    return reference -> byKey.get(new ExcerptKey(reference.page(), reference.excerptName()));
  }

  private static List<ExtensionRenderer> extensionRenderers(
      ResolverOptions resolvers, CliJson json) {
    var path = resolvers.extensionMap;
    if (path == null) {
      return List.of();
    }
    var flag = "--extension-map";
    var context = flag + " entry";
    var entries = new ArrayList<ExtensionEntry>();
    for (var entry : json.readEntries(path, flag, EXTENSION_KEYS)) {
      entries.add(
          new ExtensionEntry(
              CliJson.string(entry, "type", context),
              CliJson.requireString(entry, "key", context),
              requireNonNullElse(CliJson.string(entry, "template", context), "")));
    }
    return List.of(
        extension -> {
          for (var entry : entries) {
            if (entry.key().equals(extension.extensionKey())
                && (entry.type() == null || entry.type().equals(extension.extensionType()))) {
              // Verbatim per the ExtensionRenderer contract; unknown {tokens} become empty.
              var macroParams = extension.macroParams();
              return ExtensionOutput.replace(
                  Placeholders.render(
                      entry.template(), name -> requireNonNullElse(macroParams.value(name), "")));
            }
          }
          return ExtensionOutput.defer();
        });
  }

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
      var key = entry.getKey();
      map.put(key, requireNonNullElse(CliJson.string(object, key, flag), ""));
    }
    return map;
  }

  private record ExcerptKey(String page, @Nullable String name) {}

  private record ExtensionEntry(@Nullable String type, String key, String template) {}
}
