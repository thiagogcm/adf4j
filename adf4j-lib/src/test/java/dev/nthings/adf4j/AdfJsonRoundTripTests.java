package dev.nthings.adf4j;

import static dev.nthings.adf4j.testing.AdfJsonComparison.MAPPER;
import static dev.nthings.adf4j.testing.AdfJsonComparison.canonical;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.ParseResult;
import dev.nthings.adf4j.testing.TestResources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/// The lossless contract of {@link AdfJson}: what parses writes back as the same JSON, or the parse
/// says otherwise with an `UNPRESERVED_JSON` warning.
class AdfJsonRoundTripTests {

  private static final String UNPRESERVED = "UNPRESERVED_JSON";
  private static final Set<String> MARK_TYPES =
      Set.of(
          "strong",
          "em",
          "code",
          "strike",
          "underline",
          "subsup",
          "link",
          "textColor",
          "backgroundColor",
          "alignment",
          "indentation",
          "fontSize",
          "border",
          "annotation",
          "breakout",
          "fragment",
          "dataConsumer",
          "probeMark");

  private static final Supplier<Stream<Arguments>> fixtures =
      () -> specFixtures().map(file -> argumentSet(file, file));
  private static final Supplier<Stream<Arguments>> corpus =
      () -> corpusLines().map(line -> argumentSet(line.split("\t", 2)[0], line.split("\t", 2)[1]));

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("fixtures")
  void a_fixture_writes_back_unchanged(String file) throws IOException {
    var input = TestResources.read(file);

    var parsed = AdfJson.parse(input);

    assertThat(unpreserved(parsed)).isFalse();
    assertThat(canonical(written(parsed))).isEqualTo(canonical(input));
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("fixtures")
  void every_attribute_and_mark_survives_on_every_node(String file) throws IOException {
    var probed = MAPPER.readTree(TestResources.read(file));
    probe(probed.get("content"));
    var input = MAPPER.writeValueAsString(probed);

    var parsed = AdfJson.parse(input);

    assertThat(unpreserved(parsed)).isFalse();
    assertThat(canonical(written(parsed))).isEqualTo(canonical(input));
  }

  // Warning exactly when the output differs: no silent loss, and no false alarm.
  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("corpus")
  void off_spec_input_round_trips_or_says_it_did_not(String input) {
    var parsed = AdfJson.parse(input);
    if (!parsed.validAdfRoot()) {
      return;
    }

    var output = written(parsed);

    assertThat(unpreserved(parsed)).isEqualTo(!canonical(output).equals(canonical(input)));
    assertThat(written(AdfJson.parse(output))).as("idempotent").isEqualTo(output);
  }

  @Test
  void the_warning_names_the_first_construct_and_counts_them_all() {
    var parsed =
        AdfJson.parse(
            """
            {"type": "doc", "version": 1, "content": [
              {"type": "paragraph", "stray": 1, "content": [null]}
            ]}
            """);

    assertThat(parsed.validAdfRoot()).isTrue();
    assertThat(parsed.issues())
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.code()).isEqualTo(UNPRESERVED);
              assertThat(issue.severity()).isEqualTo(Diagnostic.Severity.WARNING);
              assertThat(issue.message())
                  .startsWith("2 ")
                  .contains("paragraph has unexpected key 'stray'");
            });
  }

  // Adds an attribute and a mark to every node and mark below `nodes`, unknown types included.
  private static void probe(JsonNode nodes) {
    if (nodes == null || !nodes.isArray()) {
      return;
    }
    for (var child : nodes) {
      if (child instanceof ObjectNode node) {
        probe(node.get("marks"));
        probe(node.get("content"));
        var attrs = node.get("attrs") instanceof ObjectNode found ? found : node.putObject("attrs");
        attrs.put("probe", 1).putArray("probeList").add("x").addNull().add(2.5);
        if (!MARK_TYPES.contains(node.path("type").asString(""))) {
          var marks = node.get("marks") instanceof ArrayNode found ? found : node.putArray("marks");
          marks.addObject().put("type", "probeMark");
        }
      }
    }
  }

  private static String written(ParseResult parsed) {
    assertThat(parsed.document()).isNotNull();
    return AdfJson.write(parsed.document());
  }

  private static boolean unpreserved(ParseResult parsed) {
    return parsed.issues().stream().anyMatch(issue -> issue.code().equals(UNPRESERVED));
  }

  private static Stream<String> specFixtures() {
    var root = TestResources.root("adf/spec");
    try (var files = Files.walk(root)) {
      return files
          .filter(path -> path.toString().endsWith(".json"))
          .map(path -> "adf/spec/" + root.relativize(path).toString().replace('\\', '/'))
          .sorted()
          .toList()
          .stream();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static Stream<String> corpusLines() {
    try {
      var corpusFile = TestResources.root("adf/characterization").resolve("corpus.tsv");
      return Files.readAllLines(corpusFile, StandardCharsets.UTF_8).stream();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
