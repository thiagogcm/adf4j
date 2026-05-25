package dev.nthings.adf4j.spec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.nthings.adf4j.AdfProcessor;
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
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final AdfProcessor PROCESSOR = new AdfProcessor();
  private static final RenderOptions DEFAULT_OPTIONS = RenderOptions.defaults("Spec Fixture");
  private static final Supplier<Stream<Arguments>> storage_specs = () -> specCases(Target.STORAGE_MARKDOWN)
      .map(SpecCase::toArguments);
  private static final Supplier<Stream<Arguments>> presentation_specs = () -> specCases(Target.PRESENTATION_HTML)
      .map(SpecCase::toArguments);

  enum Target {
    STORAGE_MARKDOWN(".storage.md"),
    PRESENTATION_HTML(".presentation.html");

    private final String suffix;

    Target(String suffix) {
      this.suffix = suffix;
    }

    String suffix() {
      return suffix;
    }
  }

  record SpecCase(String name, Target target) {
    String inputPath() {
      return "adf/spec/" + name + ".json";
    }

    String expectedPath() {
      return "adf/spec/" + name + target.suffix();
    }

    Arguments toArguments() {
      return argumentSet(name + " -> " + target.name(), this);
    }
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("storage_specs")
  void renders_storage_markdown_spec(SpecCase spec) throws IOException {
    assertRenders(spec);
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("presentation_specs")
  void renders_presentation_html_spec(SpecCase spec) throws IOException {
    assertRenders(spec);
  }

  @Test
  void every_spec_input_has_an_expected_behavior_file() throws IOException {
    var jsonSpecs = specFiles(".json").map(AdfSpecConversionTests::baseName).toList();
    var expectedSpecs = Stream.of(Target.values())
        .map(target -> specFiles(target.suffix()).map(AdfSpecConversionTests::baseName))
        .flatMap(stream -> stream)
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

    for (var path : specFiles(".json").toList()) {
      var document = MAPPER.readTree(Files.readString(path));
      var previous = seen.putIfAbsent(document, baseName(path));
      if (previous != null) {
        duplicates.add("%s duplicates %s".formatted(baseName(path), previous));
      }
    }

    assertThat(duplicates)
        .as("merge duplicate JSON payloads by colocating storage and presentation expectations")
        .isEmpty();
  }

  private static void assertRenders(SpecCase spec) throws IOException {
    var input = TestResources.read(spec.inputPath());
    var expected = TestResources.stripFinalNewline(TestResources.read(spec.expectedPath()));
    var actual = switch (spec.target()) {
      case STORAGE_MARKDOWN -> PROCESSOR.renderStorageMarkdown(input, DEFAULT_OPTIONS);
      case PRESENTATION_HTML -> PROCESSOR.renderPresentationHtml(input, DEFAULT_OPTIONS);
    };
    assertThat(actual)
        .as("case %s", spec.name())
        .isEqualToNormalizingNewlines(expected);
  }

  private static Stream<SpecCase> specCases(Target target) {
    return specFiles(target.suffix())
        .map(path -> specCase(baseName(path), target))
        .sorted((left, right) -> left.name().compareTo(right.name()));
  }

  private static SpecCase specCase(String name, Target target) {
    return new SpecCase(name, target);
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
    for (var suffix : suffixes()) {
      if (relative.endsWith(suffix)) {
        return relative.substring(0, relative.length() - suffix.length());
      }
    }
    throw new IllegalArgumentException("Unsupported spec case: " + path);
  }

  private static Collection<String> suffixes() {
    return Stream.concat(Stream.of(".json"), Stream.of(Target.values()).map(Target::suffix))
        .toList();
  }
}
