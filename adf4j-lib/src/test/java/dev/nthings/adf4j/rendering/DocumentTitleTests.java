package dev.nthings.adf4j.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.result.AdfException;
import java.util.List;
import org.junit.jupiter.api.Test;

/// Prepending `documentTitle` as a level-1 heading: escaping, one-line collapse, and failure
/// handling.
class DocumentTitleTests {

  private static final String ONE_PARAGRAPH =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          { "type": "paragraph", "content": [{ "type": "text", "text": "body" }] }
        ]
      }
      """;

  private static final String EMPTY_DOC =
      """
      { "type": "doc", "version": 1, "content": [] }
      """;

  @Test
  void document_title_is_prepended_as_an_h1_above_the_body() {
    var titled = AdfToMarkdown.with(ConversionOptions.defaults().withDocumentTitle("My Page"));
    assertThat(titled.toMarkdown(ONE_PARAGRAPH)).isEqualToNormalizingNewlines("# My Page\n\nbody");
  }

  @Test
  void document_title_on_an_empty_document_renders_just_the_heading() {
    var titled = AdfToMarkdown.with(ConversionOptions.defaults().withDocumentTitle("My Page"));
    assertThat(titled.toMarkdown(EMPTY_DOC)).isEqualToNormalizingNewlines("# My Page");
  }

  @Test
  void failed_conversion_never_emits_a_publishable_title() {
    var titled = AdfToMarkdown.with(ConversionOptions.defaults().withDocumentTitle("My Page"));
    for (var input : List.of("   ", "{\"type\":\"paragraph\",\"version\":1,\"content\":[]}")) {
      var result = titled.convert(input);
      assertThat(result.isSuccess()).isFalse();
      assertThat(result.body()).isEmpty();
      assertThatThrownBy(result::requireBody).isInstanceOf(AdfException.class);
    }
  }

  @Test
  void document_title_collapses_newlines_to_a_single_line_heading() {
    var titled =
        AdfToMarkdown.with(ConversionOptions.defaults().withDocumentTitle("Line one\nLine two"));
    assertThat(titled.toMarkdown(ONE_PARAGRAPH))
        .isEqualToNormalizingNewlines("# Line one Line two\n\nbody");
  }

  @Test
  void blank_document_title_emits_no_heading() {
    var blank = AdfToMarkdown.with(ConversionOptions.defaults().withDocumentTitle("   "));
    assertThat(blank.toMarkdown(ONE_PARAGRAPH)).isEqualToNormalizingNewlines("body");
  }

  @Test
  void document_title_escapes_markdown_and_html_punctuation() {
    var titled = AdfToMarkdown.with(ConversionOptions.defaults().withDocumentTitle("<b> & *x*"));
    assertThat(titled.toMarkdown(ONE_PARAGRAPH))
        .isEqualToNormalizingNewlines("# \\<b> \\& \\*x\\*\n\nbody");
  }

  @Test
  void document_title_of_dashes_stays_literal_text_not_a_thematic_break() {
    var titled = AdfToMarkdown.with(ConversionOptions.defaults().withDocumentTitle("---"));
    assertThat(titled.toMarkdown(ONE_PARAGRAPH)).isEqualToNormalizingNewlines("# ---\n\nbody");
  }

  @Test
  void document_title_ending_in_a_backslash_escapes_it_and_cannot_escape_the_line_end() {
    var titled = AdfToMarkdown.with(ConversionOptions.defaults().withDocumentTitle("Title\\"));
    assertThat(titled.toMarkdown(ONE_PARAGRAPH))
        .isEqualToNormalizingNewlines("# Title\\\\\n\nbody");
  }

  @Test
  void document_title_is_unaffected_by_html_visual_marks() {
    var titled =
        AdfToMarkdown.with(
            ConversionOptions.defaults().withDocumentTitle("My Page").withHtmlVisualMarks(true));
    assertThat(titled.toMarkdown(ONE_PARAGRAPH)).isEqualToNormalizingNewlines("# My Page\n\nbody");
  }

  @Test
  void document_title_is_not_deduplicated_against_an_existing_leading_heading() {
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "heading",
              "attrs": { "level": 1 },
              "content": [{ "type": "text", "text": "Body Heading" }]
            }
          ]
        }
        """;
    var titled = AdfToMarkdown.with(ConversionOptions.defaults().withDocumentTitle("My Page"));
    assertThat(titled.toMarkdown(adf)).isEqualToNormalizingNewlines("# My Page\n\n# Body Heading");
  }
}
