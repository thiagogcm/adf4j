package dev.nthings.adf4j.internal.config;

import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.AdfException;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Portable configuration shared by CLI and WASM. Callbacks are deliberately Java-only.
public final class ConversionConfig {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private ConversionConfig() {}

  /// Blank configuration is the defaults. Malformed JSON, unknown keys and wrong value types throw
  /// an {@link AdfException} carrying one `INVALID_OPTIONS` diagnostic.
  public static ConversionOptions read(String json) {
    if (json.isBlank()) return ConversionOptions.defaults();
    try {
      return parse(JSON.readTree(json));
    } catch (JacksonException invalid) {
      throw invalid(invalid.getOriginalMessage(), invalid);
    } catch (IllegalArgumentException invalid) {
      throw invalid(invalid.getMessage(), invalid);
    }
  }

  private static AdfException invalid(@Nullable String detail, Exception cause) {
    return new AdfException(
        List.of(
            new Diagnostic(
                Diagnostic.Code.INVALID_OPTIONS, "Invalid configuration: " + detail, cause)));
  }

  private static ConversionOptions parse(JsonNode root) {
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
                  options.withUnknownNodePolicy(constant(UnknownNodePolicy.values(), value, key));
              case "tableFallback" ->
                  options.withTableFallback(constant(TableFallback.values(), value, key));
              case "imageSizeAttributes" -> options.withImageSizeAttributes(bool(value, key));
              case "htmlVisualMarks" -> options.withHtmlVisualMarks(bool(value, key));
              case "collapseHardBreaks" -> options.withCollapseHardBreaks(bool(value, key));
              case "escapeParentheses" -> options.withEscapeParentheses(bool(value, key));
              // object(...) above has already rejected any other key.
              default -> throw new IllegalStateException(key);
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

  // The constant spelled in kebab case, e.g. "preserve-raw". Takes the constants, not the Class,
  // so native images need no reflection.
  private static <E extends Enum<E>> E constant(E[] constants, JsonNode node, String key) {
    var value = requiredString(node, key);
    for (var constant : constants) {
      if (kebabCase(constant).equals(value)) return constant;
    }
    throw new IllegalArgumentException(
        key
            + " must be one of: "
            + Arrays.stream(constants)
                .map(ConversionConfig::kebabCase)
                .collect(Collectors.joining(", ")));
  }

  private static String kebabCase(Enum<?> constant) {
    return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
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
