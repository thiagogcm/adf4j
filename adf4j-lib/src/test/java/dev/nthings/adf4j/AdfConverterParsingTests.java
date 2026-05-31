package dev.nthings.adf4j;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class AdfConverterParsingTests {

  private static final List<Arguments> blank_inputs = List.of(
      argumentSet("null input", (String) null),
      argumentSet("empty input", ""),
      argumentSet("whitespace input", " \n\t "));
  private static final List<Arguments> invalid_root_inputs = List.of(
      argumentSet(
          "parseable non-ADF document reports every root mismatch",
          new InvalidRootCase(
              "{\"type\":\"paragraph\",\"version\":2,\"content\":{}}",
              List.of("INVALID_ROOT_NODE", "UNSUPPORTED_VERSION", "INVALID_CONTENT"))),
      argumentSet(
          "array root is rejected before field validation",
          new InvalidRootCase("[]", List.of("INVALID_ROOT_TYPE"))),
      argumentSet(
          "missing fields report the missing required root fields",
          new InvalidRootCase(
              "{\"type\":\"doc\"}", List.of("INVALID_VERSION", "INVALID_CONTENT"))),
      argumentSet(
          "string version is not accepted as ADF version 1",
          new InvalidRootCase(
              "{\"type\":\"doc\",\"version\":\"1\",\"content\":[]}",
              List.of("INVALID_VERSION"))));

  private final AdfTestSupport testSupport = AdfTestSupport.create();
  private final AdfConverter processor = testSupport.processor();

  record InvalidRootCase(String rawAdf, List<String> issueCodes) {
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("blank_inputs")
  void parse_returns_the_empty_result_for_absent_input(String rawAdf) {
    var result = processor.parse(rawAdf);

    assertThat(result).isEqualTo(ParseResult.empty());
  }

  @Test
  void parse_reports_invalid_json_payloads() {
    var result = processor.parse("{");

    assertThat(result.document()).isNull();
    assertThat(result.validAdfRoot()).isFalse();
    assertThat(result.issues())
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.code()).isEqualTo("INVALID_JSON");
              assertThat(issue.message()).isEqualTo("Failed to parse ADF JSON payload.");
              assertThat(issue.cause()).isNotNull();
            });
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("invalid_root_inputs")
  void parse_reports_root_validation_issues_for_parseable_json(InvalidRootCase input) {
    var result = processor.parse(input.rawAdf());

    assertThat(result.document()).isNull();
    assertThat(result.validAdfRoot()).isFalse();
    assertThat(result.issues()).extracting(ParseIssue::code).containsExactlyElementsOf(input.issueCodes());
  }

  @Test
  void parse_accepts_valid_adf_roots() throws IOException {
    var result = processor.parse(testSupport.caseInput("valid-adf-root"));

    assertThat(result.document()).isNotNull();
    assertThat(result.validAdfRoot()).isTrue();
    assertThat(result.issues()).isEmpty();
    assertThat(result.document().version()).isEqualTo(1);
  }
}
