package dev.nthings.adf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import dev.nthings.adf4j.result.Diagnostic;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

class AdfToMarkdownParsingTests {

  private static final List<Arguments> blank_inputs =
      List.of(
          argumentSet("null input", (String) null),
          argumentSet("empty input", ""),
          argumentSet("whitespace input", " \n\t "));
  private static final List<Arguments> invalid_root_inputs =
      List.of(
          argumentSet(
              "parseable non-ADF document reports every root mismatch",
              new InvalidRootCase(
                  "{\"type\":\"paragraph\",\"version\":2,\"content\":{}}",
                  List.of(
                      Diagnostic.Code.INVALID_ROOT_NODE,
                      Diagnostic.Code.UNSUPPORTED_VERSION,
                      Diagnostic.Code.INVALID_CONTENT))),
          argumentSet(
              "array root is rejected before field validation",
              new InvalidRootCase("[]", List.of(Diagnostic.Code.INVALID_ROOT_TYPE))),
          argumentSet(
              "missing fields report the missing required root fields",
              new InvalidRootCase(
                  "{\"type\":\"doc\"}",
                  List.of(Diagnostic.Code.INVALID_VERSION, Diagnostic.Code.INVALID_CONTENT))),
          argumentSet(
              "non-numeric string version is not accepted as ADF version",
              new InvalidRootCase(
                  "{\"type\":\"doc\",\"version\":\"x\",\"content\":[]}",
                  List.of(Diagnostic.Code.INVALID_VERSION))));

  private final AdfTestSupport testSupport = AdfTestSupport.create();
  private final AdfToMarkdown processor = testSupport.processor();

  record InvalidRootCase(String rawAdf, List<Diagnostic.Code> issueCodes) {}

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("blank_inputs")
  void parse_returns_the_empty_result_for_absent_input(String rawAdf) {
    var result = AdfJson.parse(rawAdf);

    assertThat(result.diagnostics())
        .extracting(Diagnostic::code)
        .containsExactly(Diagnostic.Code.EMPTY_INPUT);
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void a_fractional_version_is_invalid() {
    var result = AdfJson.parse("{\"type\":\"doc\",\"version\":1.5,\"content\":[]}");

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.diagnostics())
        .extracting(Diagnostic::code)
        .containsExactly(Diagnostic.Code.INVALID_VERSION);
  }

  @Test
  void parse_reports_invalid_json_payloads() {
    var result = AdfJson.parse("{");

    assertThat(result.document()).isNull();
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.code()).isEqualTo(Diagnostic.Code.INVALID_JSON);
              assertThat(issue.message()).isEqualTo("Failed to parse ADF JSON payload.");
              assertThat(issue.cause()).isNotNull();
            });
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("invalid_root_inputs")
  void parse_reports_root_validation_issues_for_parseable_json(InvalidRootCase input) {
    var result = AdfJson.parse(input.rawAdf());

    assertThat(result.document()).isNull();
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.diagnostics())
        .extracting(Diagnostic::code)
        .containsExactlyElementsOf(input.issueCodes());
  }

  @Test
  void parse_accepts_valid_adf_roots() throws IOException {
    var result = AdfJson.parse(testSupport.caseInput("valid-adf-root"));

    assertThat(result.document()).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.document().version()).isEqualTo(1);
  }

  @Test
  void parse_accepts_a_numeric_string_version() {
    var json =
        "{\"type\":\"doc\",\"version\":\"1\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}]}]}";
    var result = AdfJson.parse(json);

    assertThat(result.document()).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.diagnostics())
        .extracting(Diagnostic::code)
        .doesNotContain(Diagnostic.Code.INVALID_VERSION);
  }

  @Test
  void parse_renders_best_effort_for_unsupported_version() {
    var json =
        "{\"type\":\"doc\",\"version\":2,\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}]}]}";
    var result = AdfJson.parse(json);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.document()).isNotNull();
    assertThat(result.document().version()).isEqualTo(2);
    assertThat(result.diagnostics())
        .extracting(Diagnostic::code)
        .containsExactly(Diagnostic.Code.UNSUPPORTED_VERSION);
    assertThat(processor.toMarkdown(json)).isEqualTo("Hi");
  }
}
