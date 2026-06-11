package dev.nthings.adf4j;

import java.util.List;

import dev.nthings.adf4j.options.ExtensionRenderer;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.Diagnostic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversionDiagnosticsTests {

  private static final String CLEAN =
      "{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}]}]}";
  private static final String UNKNOWN_NODE =
      "{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"mysteryBlock\"}]}";
  private static final String UNSUPPORTED_VERSION =
      "{\"type\":\"doc\",\"version\":2,\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}]}]}";
  private static final String UNKNOWN_MARK =
      "{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"hi\",\"marks\":[{\"type\":\"futureMark\"}]}]}]}";

  private static final String UNSUPPORTED_MACRO =
      "{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"extension\",\"attrs\":"
          + "{\"extensionType\":\"com.atlassian.confluence.macro.core\",\"extensionKey\":\"detailssummary\"}}]}";

  @Test
  void a_clean_document_is_not_flagged_lossy() {
    var result = AdfToMarkdown.create().convert(CLEAN);

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.wasLossy()).isFalse();
  }

  @Test
  void a_placeholdered_unknown_node_is_flagged_lossy_with_a_warning() {
    var result = AdfToMarkdown.create().convert(UNKNOWN_NODE);

    assertThat(result.wasLossy()).isTrue();
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.code()).isEqualTo("UNKNOWN_NODE_PLACEHOLDER");
              assertThat(issue.severity()).isEqualTo(Diagnostic.Severity.WARNING);
            });
  }

  @Test
  void a_skipped_unknown_node_is_flagged_lossy_with_a_warning() {
    var options = MarkdownOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.SKIP);

    var result = AdfToMarkdown.with(options).convert(UNKNOWN_NODE);

    assertThat(result.wasLossy()).isTrue();
    assertThat(result.diagnostics())
        .extracting(Diagnostic::code)
        .containsExactly("UNKNOWN_NODE_SKIPPED");
  }

  @Test
  void a_preserved_unknown_node_is_noted_but_not_lossy() {
    var options = MarkdownOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.PRESERVE_RAW);

    var result = AdfToMarkdown.with(options).convert(UNKNOWN_NODE);

    assertThat(result.wasLossy()).isFalse();
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.code()).isEqualTo("UNKNOWN_NODE_PRESERVED");
              assertThat(issue.severity()).isEqualTo(Diagnostic.Severity.INFO);
            });
  }

  @Test
  void an_unsupported_version_is_a_warning_and_flagged_lossy() {
    var result = AdfToMarkdown.create().convert(UNSUPPORTED_VERSION);

    assertThat(result.wasLossy()).isTrue();
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.code()).isEqualTo("UNSUPPORTED_VERSION");
              assertThat(issue.severity()).isEqualTo(Diagnostic.Severity.WARNING);
            });
  }

  @Test
  void a_dropped_unknown_mark_is_flagged_lossy_even_when_the_text_renders() {
    var result = AdfToMarkdown.create().convert(UNKNOWN_MARK);

    // The text still renders; the unsupported mark is dropped but reported, not silently lost.
    assertThat(result.body()).isEqualTo("hi");
    assertThat(result.wasLossy()).isTrue();
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.code()).isEqualTo("UNKNOWN_MARK_DROPPED");
              assertThat(issue.severity()).isEqualTo(Diagnostic.Severity.WARNING);
            });
  }

  @Test
  void preserve_raw_still_reports_dropped_marks_alongside_preserved_nodes() {
    var json =
        "{\"type\":\"doc\",\"version\":1,\"content\":["
            + "{\"type\":\"mysteryBlock\"},"
            + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"hi\",\"marks\":[{\"type\":\"futureMark\"}]}]}"
            + "]}";
    var options = MarkdownOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.PRESERVE_RAW);

    var result = AdfToMarkdown.with(options).convert(json);

    // The node is preserved (INFO) but the mark is still dropped (WARNING), so the doc is lossy.
    assertThat(result.diagnostics())
        .extracting(Diagnostic::code)
        .containsExactly("UNKNOWN_NODE_PRESERVED", "UNKNOWN_MARK_DROPPED");
    assertThat(result.wasLossy()).isTrue();
  }

  @Test
  void a_fatal_parse_error_is_flagged_lossy() {
    var result = AdfToMarkdown.create().convert("{");

    assertThat(result.wasLossy()).isTrue();
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(issue -> assertThat(issue.severity()).isEqualTo(Diagnostic.Severity.ERROR));
  }

  @Test
  void an_unsupported_macro_is_flagged_lossy_with_a_warning() {
    var result = AdfToMarkdown.create().convert(UNSUPPORTED_MACRO);

    assertThat(result.wasLossy()).isTrue();
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.code()).isEqualTo("UNSUPPORTED_MACRO");
              assertThat(issue.severity()).isEqualTo(Diagnostic.Severity.WARNING);
              assertThat(issue.message()).contains("detailssummary");
            });
  }

  @Test
  void a_macro_with_attribute_text_is_not_flagged_as_unsupported() {
    var json =
        "{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"extension\",\"attrs\":"
            + "{\"extensionType\":\"com.atlassian.confluence.macro.core\",\"extensionKey\":\"detailssummary\","
            + "\"text\":\"Static fallback\"}}]}";

    var result = AdfToMarkdown.create().convert(json);

    assertThat(result.body()).contains("Static fallback");
    assertThat(result.diagnostics()).extracting(Diagnostic::code).doesNotContain("UNSUPPORTED_MACRO");
    assertThat(result.wasLossy()).isFalse();
  }

  @Test
  void a_handled_macro_does_not_warn() {
    var json =
        "{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"extension\",\"attrs\":"
            + "{\"extensionType\":\"com.atlassian.confluence.macro.core\",\"extensionKey\":\"children\"}}]}";

    var result = AdfToMarkdown.create().convert(json);

    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.wasLossy()).isFalse();
  }

  @Test
  void a_pagetree_macro_is_handled_and_does_not_warn() {
    var json =
        "{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"extension\",\"attrs\":"
            + "{\"extensionType\":\"com.atlassian.confluence.macro.core\",\"extensionKey\":\"pagetree\"}}]}";

    var result = AdfToMarkdown.create().convert(json);

    assertThat(result.body()).isEqualTo("{{pagetree}}");
    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.wasLossy()).isFalse();
  }

  @Test
  void an_excerpt_bodied_extension_does_not_warn() {
    var json =
        "{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"bodiedExtension\",\"attrs\":"
            + "{\"extensionType\":\"com.atlassian.confluence.macro.core\",\"extensionKey\":\"excerpt\"},"
            + "\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"inside excerpt\"}]}]}]}";

    var result = AdfToMarkdown.create().convert(json);

    assertThat(result.body()).contains("inside excerpt");
    assertThat(result.diagnostics()).extracting(Diagnostic::code).doesNotContain("UNSUPPORTED_MACRO");
    assertThat(result.wasLossy()).isFalse();
  }

  @Test
  void a_custom_renderer_suppresses_the_unsupported_macro_warning() {
    ExtensionRenderer detailssummary =
        extension ->
            "detailssummary".equals(extension.extensionKey())
                ? "- a row"
                : null;
    var options = MarkdownOptions.defaults().withExtensionRenderers(List.of(detailssummary));

    var handled = AdfToMarkdown.with(options).convert(UNSUPPORTED_MACRO);
    assertThat(handled.diagnostics()).extracting(Diagnostic::code).doesNotContain("UNSUPPORTED_MACRO");
    assertThat(handled.wasLossy()).isFalse();

    var fallback = AdfToMarkdown.create().convert(UNSUPPORTED_MACRO);
    assertThat(fallback.diagnostics()).extracting(Diagnostic::code).containsExactly("UNSUPPORTED_MACRO");
  }

  @Test
  void repeated_unsupported_macros_aggregate_into_one_diagnostic() {
    var json =
        "{\"type\":\"doc\",\"version\":1,\"content\":["
            + "{\"type\":\"extension\",\"attrs\":{\"extensionType\":\"com.atlassian.confluence.macro.core\",\"extensionKey\":\"widget\"}},"
            + "{\"type\":\"extension\",\"attrs\":{\"extensionType\":\"com.atlassian.confluence.macro.core\",\"extensionKey\":\"widget\"}},"
            + "{\"type\":\"extension\",\"attrs\":{\"extensionType\":\"com.atlassian.confluence.macro.core\",\"extensionKey\":\"detailssummary\"}}"
            + "]}";

    var result = AdfToMarkdown.create().convert(json);

    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.code()).isEqualTo("UNSUPPORTED_MACRO");
              assertThat(issue.message()).contains("2 unsupported macro(s)");
              assertThat(issue.message()).contains("widget");
              assertThat(issue.message()).contains("detailssummary");
            });
  }

  @Test
  void an_inline_unsupported_macro_is_flagged() {
    var json =
        "{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"paragraph\",\"content\":["
            + "{\"type\":\"text\",\"text\":\"See \"},"
            + "{\"type\":\"inlineExtension\",\"attrs\":{\"extensionType\":\"com.atlassian.confluence.macro.core\",\"extensionKey\":\"widget\"}}"
            + "]}]}";

    var result = AdfToMarkdown.create().convert(json);

    assertThat(result.wasLossy()).isTrue();
    assertThat(result.diagnostics()).extracting(Diagnostic::code).containsExactly("UNSUPPORTED_MACRO");
  }
}
