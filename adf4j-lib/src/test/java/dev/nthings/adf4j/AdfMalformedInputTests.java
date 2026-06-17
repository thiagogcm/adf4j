package dev.nthings.adf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/// Degradation tests for structurally-malformed ADF. The converter is expected to be lenient: a
/// misplaced child, a missing body, a stray inline, or a JSON `null` in a content array must
/// never throw — it should drop the offending bit and keep rendering whatever is salvageable. Each
/// case asserts both that `toMarkdown` returns (no exception) and that the surviving output is
/// sensible.
class AdfMalformedInputTests {

  private final AdfToMarkdown processor = AdfToMarkdown.create();

  @Test
  void table_row_with_a_non_cell_child_does_not_throw() {
    // A paragraph where a tableCell belongs: the bogus child is dropped, leaving no renderable
    // cells
    // (under the default promote-first-row fallback an all-empty table renders nothing).
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "table", "content": [
              {"type": "tableRow", "content": [
                {"type": "paragraph", "content": [{"type": "text", "text": "stray"}]}
              ]}
            ]}
          ]
        }
        """;

    assertThatCode(() -> processor.toMarkdown(adf)).doesNotThrowAnyException();
    var markdown = processor.toMarkdown(adf);
    assertThat(markdown).isNotNull().isEmpty();
  }

  @Test
  void list_item_with_no_paragraph_does_not_throw() {
    // An empty listItem still yields a bullet marker rather than blowing up.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "bulletList", "content": [
              {"type": "listItem", "content": []}
            ]}
          ]
        }
        """;

    assertThatCode(() -> processor.toMarkdown(adf)).doesNotThrowAnyException();
    var markdown = processor.toMarkdown(adf);
    assertThat(markdown).isNotNull().startsWith("-");
  }

  @Test
  void table_row_with_a_stray_text_node_does_not_throw() {
    // Loose inline text directly under a tableRow (where cells belong) is dropped, leaving no
    // renderable cells (the default promote-first-row fallback renders an all-empty table as
    // nothing).
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "table", "content": [
              {"type": "tableRow", "content": [
                {"type": "text", "text": "loose"}
              ]}
            ]}
          ]
        }
        """;

    assertThatCode(() -> processor.toMarkdown(adf)).doesNotThrowAnyException();
    var markdown = processor.toMarkdown(adf);
    assertThat(markdown).isNotNull().isEmpty();
  }

  @Test
  void paragraph_with_a_null_content_element_does_not_throw_and_keeps_the_rest() {
    // A JSON null in the inline content array is skipped; the surrounding text runs survive.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [
              {"type": "text", "text": "before "},
              null,
              {"type": "text", "text": "after"}
            ]}
          ]
        }
        """;

    assertThatCode(() -> processor.toMarkdown(adf)).doesNotThrowAnyException();
    var markdown = processor.toMarkdown(adf);
    assertThat(markdown).isNotNull().isEqualTo("before after");
  }

  @Test
  void doc_with_a_null_top_level_content_element_does_not_throw_and_keeps_the_rest() {
    // A JSON null among the top-level blocks is skipped; both real paragraphs survive.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [{"type": "text", "text": "alpha"}]},
            null,
            {"type": "paragraph", "content": [{"type": "text", "text": "beta"}]}
          ]
        }
        """;

    assertThatCode(() -> processor.toMarkdown(adf)).doesNotThrowAnyException();
    var markdown = processor.toMarkdown(adf);
    assertThat(markdown).isNotNull().contains("alpha").contains("beta");
  }

  @Test
  void json_nested_past_the_parse_depth_cap_degrades_to_an_invalid_json_diagnostic() {
    // 700 blockquote frames (~1400 JSON levels) exceed the nesting cap; the parser catches it and
    // degrades to an empty body with an INVALID_JSON diagnostic instead of throwing.
    var depth = 700;
    var json = new StringBuilder("{\"type\":\"doc\",\"version\":1,\"content\":[");
    for (var i = 0; i < depth; i++) {
      json.append("{\"type\":\"blockquote\",\"content\":[");
    }
    json.append("{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"deep\"}]}");
    for (var i = 0; i < depth; i++) {
      json.append("]}");
    }
    json.append("]}");
    var adf = json.toString();

    assertThatCode(() -> processor.convert(adf)).doesNotThrowAnyException();
    var result = processor.convert(adf);
    assertThat(result.body()).isBlank();
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(issue -> assertThat(issue.code()).isEqualTo("INVALID_JSON"));
  }

  @Test
  void moderately_nested_document_stays_under_the_cap_and_still_converts() {
    // The depth cap must not reject realistic nesting: 20 blockquote frames convert normally.
    var depth = 20;
    var json = new StringBuilder("{\"type\":\"doc\",\"version\":1,\"content\":[");
    for (var i = 0; i < depth; i++) {
      json.append("{\"type\":\"blockquote\",\"content\":[");
    }
    json.append("{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"deep\"}]}");
    for (var i = 0; i < depth; i++) {
      json.append("]}");
    }
    json.append("]}");
    var adf = json.toString();

    assertThatCode(() -> processor.toMarkdown(adf)).doesNotThrowAnyException();
    assertThat(processor.toMarkdown(adf)).contains(">").contains("deep");
  }

  @Test
  void date_with_an_out_of_range_timestamp_does_not_throw() {
    // Long.MIN_VALUE drives Math.abs negative; the conversion must degrade, not throw a
    // DateTimeException.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [
              {"type": "date", "attrs": {"timestamp": "-9223372036854775808"}}
            ]}
          ]
        }
        """;

    assertThatCode(() -> processor.toMarkdown(adf)).doesNotThrowAnyException();
    assertThat(processor.toMarkdown(adf)).isNotBlank();
  }

  @Test
  void node_with_an_unknown_type_does_not_throw_and_keeps_known_siblings() {
    // An unrecognised block degrades (placeholder under the default policy) and never derails the
    // known paragraph beside it.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [{"type": "text", "text": "keep"}]},
            {"type": "flibberType", "attrs": {"x": 1}, "content": []}
          ]
        }
        """;

    assertThatCode(() -> processor.toMarkdown(adf)).doesNotThrowAnyException();
    var markdown = processor.toMarkdown(adf);
    assertThat(markdown).isNotNull().contains("keep");
  }
}
