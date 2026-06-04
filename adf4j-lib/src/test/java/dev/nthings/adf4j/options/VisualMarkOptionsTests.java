package dev.nthings.adf4j.options;

import dev.nthings.adf4j.AdfToMarkdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisualMarkOptionsTests {

  private static final String TEXT_COLOR =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              {
                "type": "text",
                "text": "danger",
                "marks": [ { "type": "textColor", "attrs": { "color": "#ff0000" } } ]
              }
            ]
          }
        ]
      }
      """;

  private static final String COLOR_AND_BACKGROUND =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              {
                "type": "text",
                "text": "marked",
                "marks": [
                  { "type": "textColor", "attrs": { "color": "#ff0000" } },
                  { "type": "backgroundColor", "attrs": { "color": "#00ff00" } }
                ]
              }
            ]
          }
        ]
      }
      """;

  private static final String FONT_SIZE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              {
                "type": "text",
                "text": "tiny",
                "marks": [ { "type": "fontSize", "attrs": { "fontSize": "small" } } ]
              }
            ]
          }
        ]
      }
      """;

  private static final String CENTERED_PARAGRAPH =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "marks": [ { "type": "alignment", "attrs": { "align": "center" } } ],
            "content": [ { "type": "text", "text": "middle" } ]
          }
        ]
      }
      """;

  @Test
  void default_drops_visual_only_marks() {
    assertThat(AdfToMarkdown.create().toMarkdown(TEXT_COLOR).strip()).isEqualTo("danger");
    assertThat(AdfToMarkdown.create().toMarkdown(COLOR_AND_BACKGROUND).strip()).isEqualTo("marked");
    assertThat(AdfToMarkdown.create().toMarkdown(FONT_SIZE).strip()).isEqualTo("tiny");
  }

  @Test
  void opt_in_preserves_text_color_as_a_span_style() {
    var options = MarkdownOptions.defaults().withHtmlVisualMarks(true);
    var markdown = AdfToMarkdown.with(options).toMarkdown(TEXT_COLOR).strip();

    assertThat(markdown).isEqualTo("<span style=\"color:#ff0000\">danger</span>");
  }

  @Test
  void opt_in_combines_multiple_visual_marks_into_one_span() {
    var options = MarkdownOptions.defaults().withHtmlVisualMarks(true);
    var markdown = AdfToMarkdown.with(options).toMarkdown(COLOR_AND_BACKGROUND).strip();

    assertThat(markdown)
        .isEqualTo("<span style=\"color:#ff0000; background-color:#00ff00\">marked</span>");
  }

  @Test
  void opt_in_preserves_font_size_keyword() {
    var options = MarkdownOptions.defaults().withHtmlVisualMarks(true);
    var markdown = AdfToMarkdown.with(options).toMarkdown(FONT_SIZE).strip();

    assertThat(markdown).isEqualTo("<span style=\"font-size:small\">tiny</span>");
  }

  @Test
  void opt_in_wraps_a_centered_block_in_a_div() {
    var options = MarkdownOptions.defaults().withHtmlVisualMarks(true);
    var markdown = AdfToMarkdown.with(options).toMarkdown(CENTERED_PARAGRAPH).strip();

    assertThat(markdown).isEqualTo("<div align=\"center\">\n\nmiddle\n\n</div>");
  }

  @Test
  void default_drops_alignment_without_a_div() {
    assertThat(AdfToMarkdown.create().toMarkdown(CENTERED_PARAGRAPH).strip()).isEqualTo("middle");
  }
}
