package dev.nthings.adf4j.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.Diagnostic.Code;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

/// `AdfJson.parse`: when a payload yields a document, and how it says why not. Malformed content
/// inside a valid root is covered case by case in the golden corpus.
class ParseTests {

  private static final String HI =
      """
      [{"type": "paragraph", "content": [{"type": "text", "text": "Hi"}]}]""";

  private static final List<Arguments> blank_inputs =
      List.of(
          argumentSet("null", (String) null),
          argumentSet("empty", ""),
          argumentSet("whitespace", " \n\t "));

  private static final List<Arguments> invalid_roots =
      List.of(
          argumentSet(
              "an array root stops before field checks",
              new InvalidRoot("[]", List.of(issue(Code.INVALID_ROOT_TYPE, "")))),
          argumentSet(
              "a non-doc root reports every mismatch",
              new InvalidRoot(
                  "{\"type\":\"paragraph\",\"version\":2,\"content\":{}}",
                  List.of(
                      issue(Code.INVALID_ROOT_NODE, "/type"),
                      issue(Code.UNSUPPORTED_VERSION, "/version"),
                      issue(Code.INVALID_CONTENT, "/content")))),
          argumentSet(
              "missing fields",
              new InvalidRoot(
                  "{\"type\":\"doc\"}",
                  List.of(
                      issue(Code.INVALID_VERSION, "/version"),
                      issue(Code.INVALID_CONTENT, "/content")))),
          argumentSet(
              "a non-numeric string version",
              new InvalidRoot(
                  "{\"type\":\"doc\",\"version\":\"x\",\"content\":[]}",
                  List.of(issue(Code.INVALID_VERSION, "/version")))),
          argumentSet(
              "a fractional version",
              new InvalidRoot(
                  "{\"type\":\"doc\",\"version\":1.5,\"content\":[]}",
                  List.of(issue(Code.INVALID_VERSION, "/version")))));

  record Issue(Code code, String path) {}

  record InvalidRoot(String json, List<Issue> issues) {}

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("blank_inputs")
  void absent_input_yields_no_document(String json) {
    var result = AdfJson.parse(json);

    assertThat(result.document()).isNull();
    assertThat(result.diagnostics()).extracting(Diagnostic::code).containsExactly(Code.EMPTY_INPUT);
  }

  @Test
  void unreadable_json_yields_no_document_and_keeps_the_cause() {
    var result = AdfJson.parse("{");

    assertThat(result.document()).isNull();
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.code()).isEqualTo(Code.INVALID_JSON);
              assertThat(issue.severity()).isEqualTo(Diagnostic.Severity.ERROR);
              assertThat(issue.cause()).isNotNull();
            });
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("invalid_roots")
  void an_invalid_root_yields_no_document_and_locates_each_problem(InvalidRoot root) {
    var result = AdfJson.parse(root.json());

    assertThat(result.document()).isNull();
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.diagnostics())
        .map(issue -> new Issue(issue.code(), issue.paths().getFirst()))
        .containsExactlyElementsOf(root.issues());
  }

  // A string version still parses, but writes back as a number, so the change is reported.
  @Test
  void a_numeric_string_version_is_accepted_and_reported_as_not_preserved() {
    var result = AdfJson.parse("{\"type\":\"doc\",\"version\":\"1\",\"content\":" + HI + "}");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.document().version()).isEqualTo(1);
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.code()).isEqualTo(Code.UNPRESERVED_JSON);
              assertThat(issue.paths()).containsExactly("/version");
            });
  }

  @Test
  void an_unsupported_version_parses_best_effort_with_a_warning() {
    var result = AdfJson.parse("{\"type\":\"doc\",\"version\":2,\"content\":" + HI + "}");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.document().version()).isEqualTo(2);
    assertThat(result.document().content()).hasSize(1);
    assertThat(result.diagnostics())
        .extracting(Diagnostic::code)
        .containsExactly(Code.UNSUPPORTED_VERSION);
  }

  // The nesting cap protects the recursive parse and render from stack overflow, and must not
  // reject realistic depth.
  @Test
  void nesting_past_the_cap_degrades_to_invalid_json_while_realistic_depth_converts() {
    var converter = AdfToMarkdown.create();

    var deep = converter.convert(nestedBlockquotes(700));
    var realistic = converter.convert(nestedBlockquotes(20));

    assertThat(deep.body()).isEmpty();
    assertThat(deep.diagnostics()).extracting(Diagnostic::code).containsExactly(Code.INVALID_JSON);
    assertThat(realistic.diagnostics()).isEmpty();
    assertThat(realistic.body()).isEqualTo("> ".repeat(20) + "deep");
  }

  private static String nestedBlockquotes(int depth) {
    var open = "{\"type\":\"blockquote\",\"content\":[".repeat(depth);
    var leaf = "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"deep\"}]}";
    return "{\"type\":\"doc\",\"version\":1,\"content\":["
        + open
        + leaf
        + "]}".repeat(depth)
        + "]}";
  }

  private static Issue issue(Code code, String path) {
    return new Issue(code, path);
  }
}
