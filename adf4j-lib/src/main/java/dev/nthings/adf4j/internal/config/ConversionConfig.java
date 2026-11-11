package dev.nthings.adf4j.internal.config;

import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Portable configuration shared by CLI and WASM. Callbacks are deliberately Java-only.
public final class ConversionConfig {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private ConversionConfig() {}

  public static ConversionOptions read(String json) {
    var root = JSON.readTree(json);
    object(root, Set.of("markdown", "context"));
    var options = ConversionOptions.defaults();
    var markdown = root.get("markdown");
    if (markdown != null) {
      object(
          markdown,
          Set.of(
              "unknownNodePolicy",
              "tableFallback",
              "imageSizeAttributes",
              "htmlVisualMarks",
              "collapseHardBreaks",
              "escapeParentheses"));
      for (var entry : markdown.properties()) {
        var key = entry.getKey();
        var value = entry.getValue();
        options =
            switch (key) {
              case "unknownNodePolicy" ->
                  options.withUnknownNodePolicy(
                      UnknownNodePolicy.valueOf(
                          requiredString(value, key).toUpperCase(Locale.ROOT).replace('-', '_')));
              case "tableFallback" ->
                  options.withTableFallback(
                      TableFallback.valueOf(
                          requiredString(value, key).toUpperCase(Locale.ROOT).replace('-', '_')));
              case "imageSizeAttributes" -> options.withImageSizeAttributes(bool(value, key));
              case "htmlVisualMarks" -> options.withHtmlVisualMarks(bool(value, key));
              case "collapseHardBreaks" -> options.withCollapseHardBreaks(bool(value, key));
              case "escapeParentheses" -> options.withEscapeParentheses(bool(value, key));
              default -> throw new IllegalArgumentException("Unknown option: " + key);
            };
      }
    }
    var context = root.get("context");
    if (context != null) {
      object(context, Set.of("title", "attachments"));
      options = options.withDocumentTitle(string(context, "title"));
      var attachments = context.get("attachments");
      if (attachments != null) {
        if (!attachments.isArray())
          throw new IllegalArgumentException("attachments must be an array");
        var entries = new ArrayList<AttachmentReference>();
        for (var entry : attachments) {
          object(entry, Set.of("fileId", "title", "mediaType", "downloadUrl"));
          var id = string(entry, "fileId");
          if (id == null || id.isBlank())
            throw new IllegalArgumentException("attachment fileId is required");
          entries.add(
              new AttachmentReference(
                  id,
                  string(entry, "title"),
                  string(entry, "mediaType"),
                  string(entry, "downloadUrl")));
        }
        options = options.withAttachments(entries);
      }
    }
    return options;
  }

  private static void object(JsonNode node, Set<String> keys) {
    if (!node.isObject()) throw new IllegalArgumentException("Expected a configuration object");
    for (var key : node.propertyNames())
      if (!keys.contains(key))
        throw new IllegalArgumentException("Unknown configuration key: " + key);
  }

  private static boolean bool(JsonNode node, String key) {
    if (!node.isBoolean()) throw new IllegalArgumentException(key + " must be a boolean");
    return node.asBoolean();
  }

  private static String requiredString(JsonNode node, String key) {
    if (!node.isString()) throw new IllegalArgumentException(key + " must be a string");
    return node.asString();
  }

  private static @Nullable String string(JsonNode node, String key) {
    var value = node.get(key);
    return value == null || value.isNull() ? null : requiredString(value, key);
  }
}
