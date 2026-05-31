package dev.nthings.adf4j.spec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.nthings.adf4j.AdfConverter;
import dev.nthings.adf4j.RenderOptions;
import dev.nthings.adf4j.testing.TestResources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class AdfSpecConversionTests {

  private static final Path SPEC_ROOT = TestResources.root("adf/spec");
  private static final String INPUT_SUFFIX = ".json";
  private static final String EXPECTED_SUFFIX = ".md";
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final AdfConverter PROCESSOR = new AdfConverter();
  private static final RenderOptions DEFAULT_OPTIONS = RenderOptions.defaults();
  private static final Supplier<Stream<Arguments>> markdown_specs = () -> specCases().map(SpecCase::toArguments);

  record SpecCase(String name) {
    String inputPath() {
      return "adf/spec/" + name + INPUT_SUFFIX;
    }

    String expectedPath() {
      return "adf/spec/" + name + EXPECTED_SUFFIX;
    }

    Arguments toArguments() {
      return argumentSet(name, this);
    }
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("markdown_specs")
  void renders_markdown_spec(SpecCase spec) throws IOException {
    var input = TestResources.read(spec.inputPath());
    var expected = TestResources.stripFinalNewline(TestResources.read(spec.expectedPath()));
    var actual = PROCESSOR.toMarkdown(input, DEFAULT_OPTIONS);
    assertThat(actual)
        .as("case %s", spec.name())
        .isEqualToNormalizingNewlines(expected);
  }

  @Test
  void every_spec_input_has_an_expected_markdown_file() throws IOException {
    var jsonSpecs = specFiles(INPUT_SUFFIX).map(AdfSpecConversionTests::baseName).toList();
    var expectedSpecs = specFiles(EXPECTED_SUFFIX)
        .map(AdfSpecConversionTests::baseName)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    assertThat(jsonSpecs)
        .isNotEmpty()
        .allSatisfy(name -> assertThat(expectedSpecs).contains(name));
    assertThat(expectedSpecs).allSatisfy(name -> assertThat(jsonSpecs).contains(name));
  }

  @Test
  void discoverable_spec_inputs_do_not_duplicate_the_same_adf_payload() throws IOException {
    var seen = new LinkedHashMap<JsonNode, String>();
    var duplicates = new ArrayList<String>();

    for (var path : specFiles(INPUT_SUFFIX).toList()) {
      var document = MAPPER.readTree(Files.readString(path));
      var previous = seen.putIfAbsent(document, baseName(path));
      if (previous != null) {
        duplicates.add("%s duplicates %s".formatted(baseName(path), previous));
      }
    }

    assertThat(duplicates)
        .as("merge duplicate JSON payloads instead of colocating identical fixtures")
        .isEmpty();
  }

  private static Stream<SpecCase> specCases() {
    return specFiles(EXPECTED_SUFFIX)
        .map(path -> new SpecCase(baseName(path)))
        .sorted((left, right) -> left.name().compareTo(right.name()));
  }

  private static Stream<Path> specFiles(String suffix) {
    try (var files = Files.walk(SPEC_ROOT)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> SPEC_ROOT.relativize(path).getNameCount() > 1)
          .filter(path -> path.getFileName().toString().endsWith(suffix))
          .sorted()
          .toList()
          .stream();
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to discover ADF spec cases", exception);
    }
  }

  private static String baseName(Path path) {
    var relative = SPEC_ROOT.relativize(path).toString().replace('\\', '/');
    for (var suffix : new String[] {INPUT_SUFFIX, EXPECTED_SUFFIX}) {
      if (relative.endsWith(suffix)) {
        return relative.substring(0, relative.length() - suffix.length());
      }
    }
    throw new IllegalArgumentException("Unsupported spec case: " + path);
  }
}
