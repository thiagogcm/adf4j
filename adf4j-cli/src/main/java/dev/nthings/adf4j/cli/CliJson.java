package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.result.OperationResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/// Tree-mode-only JSON for the CLI (no record databind), so it adds no reflection surface to the
/// native image. The mapper is hardened with {@link StreamReadConstraints} so a hostile map file
/// fails cleanly instead of overflowing the stack. Every shape problem in a map file is a usage
/// error (exit 2), named after its flag.
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

  ObjectNode result(OperationResult result) {
    return (ObjectNode) mapper.readTree(AdfJson.writeResult(result));
  }

  static ObjectNode object() {
    return JsonNodeFactory.instance.objectNode();
  }

  static ArrayNode array() {
    return JsonNodeFactory.instance.arrayNode();
  }

  /// The CLI spelling of a library enum constant, in option values and JSON output alike.
  static String kebabCase(Enum<?> constant) {
    return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  /// Serializes a tree node: `pretty` two-space-indents (forcing `\n` newlines so output is
  /// byte-identical across platforms), else single-line.
  String write(JsonNode node, boolean pretty) {
    return pretty
        ? mapper.writer().with(PRETTY_LF).writeValueAsString(node)
        : mapper.writeValueAsString(node);
  }

  /// Reads and parses a map/data file supplied via `flag`. A missing/unreadable file is an I/O
  /// error (exit 1); malformed JSON is a usage error (exit 2). Neither leaks a stack trace to
  /// stdout.
  JsonNode readFile(Path path, String flag) {
    var raw = CliIo.read(path, flag + " file");
    try {
      return mapper.readTree(raw);
    } catch (JacksonException exception) {
      throw CliException.usage(
          "invalid JSON in " + flag + " file '" + path + "': " + exception.getOriginalMessage());
    }
  }

  /// Reads a map file holding an array of entries; see {@link #entries}.
  List<ObjectNode> readEntries(String path, String flag, Set<String> allowedKeys) {
    return entries(readFile(Path.of(path), flag), flag, allowedKeys);
  }

  /// The elements of `array`, each an object with no keys outside `allowedKeys`. `context` names
  /// the array in errors.
  static List<ObjectNode> entries(JsonNode array, String context, Set<String> allowedKeys) {
    if (!(array instanceof ArrayNode elements)) {
      throw CliException.usage(context + " must be a JSON array");
    }
    var entries = new ArrayList<ObjectNode>(elements.size());
    for (var element : elements) {
      var entry = requireObject(element, context + " entry");
      rejectUnknownKeys(entry, allowedKeys, context + " entry");
      entries.add(entry);
    }
    return entries;
  }

  /// The node as an object, or a usage error naming `context`.
  static ObjectNode requireObject(JsonNode node, String context) {
    if (node instanceof ObjectNode object) {
      return object;
    }
    throw CliException.usage(context + " must be a JSON object");
  }

  /// Rejects unknown keys on a CLI-owned schema object so a typo'd key fails loudly (exit 2).
  static void rejectUnknownKeys(ObjectNode object, Set<String> allowed, String context) {
    for (var entry : object.properties()) {
      if (!allowed.contains(entry.getKey())) {
        throw CliException.usage("unknown key '" + entry.getKey() + "' in " + context);
      }
    }
  }

  /// A required, non-blank string field, or a usage error.
  static String requireString(JsonNode object, String field, String context) {
    var value = string(object, field, context);
    if (value == null || value.isBlank()) {
      throw CliException.usage("missing '" + field + "' in " + context);
    }
    return value;
  }

  /// A scalar field as a string (a number keeps its JSON spelling), `null` when absent or JSON
  /// null, or a usage error when it is an object or array.
  static @Nullable String string(JsonNode object, String field, String context) {
    var value = object.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isContainer()) {
      throw CliException.usage("'" + field + "' in " + context + " must be a scalar value");
    }
    return value.asString();
  }

  /// An integer field, `0` when absent or JSON null, or a usage error.
  static int integer(JsonNode object, String field, String context) {
    var value = string(object, field, context);
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(value.strip());
    } catch (NumberFormatException exception) {
      throw CliException.usage("'" + field + "' in " + context + " must be an integer");
    }
  }
}
