package dev.nthings.adf4j.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/// Tree-mode-only JSON for the CLI (no record databind), so it adds no reflection surface to the
/// native/wasm image. The mapper is hardened with {@link StreamReadConstraints} so a hostile map
/// file fails cleanly instead of overflowing the stack.
final class CliJson {

  // Shallow lookup tables: 64 levels is far beyond any real file yet caps a hostile one.
  private static final int MAX_NESTING_DEPTH = 64;
  private static final int MAX_STRING_LENGTH = 20_000_000;
  private static final int MAX_NUMBER_LENGTH = 1_000;

  // Pin the pretty-printer newline to '\n' (Jackson defaults to System.lineSeparator()) so JSON
  // output is byte-identical across platforms.
  private static final DefaultPrettyPrinter PRETTY_LF =
      new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n"));

  private final JsonMapper mapper;

  CliJson() {
    var factory =
        JsonFactory.builder()
            .streamReadConstraints(
                StreamReadConstraints.builder()
                    .maxNestingDepth(MAX_NESTING_DEPTH)
                    .maxStringLength(MAX_STRING_LENGTH)
                    .maxNumberLength(MAX_NUMBER_LENGTH)
                    .build())
            .build();
    this.mapper = JsonMapper.builder(factory).build();
  }

  ObjectNode object() {
    return mapper.createObjectNode();
  }

  ArrayNode array() {
    return mapper.createArrayNode();
  }

  /// Serializes a tree node: `pretty` two-space-indents (forcing `\n` newlines so output is
  /// byte-identical across platforms), else single-line.
  String write(JsonNode node, boolean pretty) {
    return pretty
        ? mapper.writer().with(PRETTY_LF).writeValueAsString(node)
        : mapper.writeValueAsString(node);
  }

  /// Reads and parses a map/data file supplied via `flag`. A missing/unreadable file is an I/O
  /// error (exit 2); malformed JSON is a usage error (exit 1). Neither leaks a stack trace to
  /// stdout.
  JsonNode readFile(Path path, String flag) {
    String raw;
    try {
      raw = Files.readString(path, StandardCharsets.UTF_8);
    } catch (NoSuchFileException exception) {
      throw CliException.io(
          "failed to read " + flag + " file '" + path + "': not found", exception);
    } catch (IOException exception) {
      throw CliException.io(
          "failed to read " + flag + " file '" + path + "': " + exception.getMessage(), exception);
    }
    try {
      return mapper.readTree(raw);
    } catch (JacksonException exception) {
      throw CliException.usage(
          "invalid JSON in " + flag + " file '" + path + "': " + exception.getOriginalMessage());
    }
  }

  /// The node as an object, or a usage error naming `flag`.
  static ObjectNode requireObject(JsonNode node, String flag) {
    if (node instanceof ObjectNode object) {
      return object;
    }
    throw CliException.usage(flag + " file must be a JSON object");
  }

  /// The node as an array, or a usage error naming `flag`.
  static ArrayNode requireArray(JsonNode node, String flag) {
    if (node instanceof ArrayNode array) {
      return array;
    }
    throw CliException.usage(flag + " file must be a JSON array");
  }

  /// Rejects unknown keys on a CLI-owned schema object so a typo'd key fails loudly (exit 1).
  static void rejectUnknownKeys(ObjectNode object, Set<String> allowed, String context) {
    for (var entry : object.properties()) {
      if (!allowed.contains(entry.getKey())) {
        throw CliException.usage("unknown key '" + entry.getKey() + "' in " + context);
      }
    }
  }

  /// A required, non-blank string field, or a usage error.
  static String requireString(JsonNode object, String field, String context) {
    var value = string(object, field);
    if (value == null || value.isBlank()) {
      throw CliException.usage("missing '" + field + "' in " + context);
    }
    return value;
  }

  /// A string field, or `null` when absent or JSON null.
  static @Nullable String string(JsonNode object, String field) {
    var value = object.get(field);
    return value == null || value.isNull() ? null : value.asString();
  }
}
