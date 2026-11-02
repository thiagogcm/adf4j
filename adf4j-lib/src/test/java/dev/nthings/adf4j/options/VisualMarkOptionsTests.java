package dev.nthings.adf4j.options;

import static dev.nthings.adf4j.testing.AdfSnippets.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
            "marks": [ { "type": "fontSize", "attrs": { "fontSize": "small" } } ],
            "content": [
              {
                "type": "text",
                "text": "tiny"
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

  private static String visual(String json) {
    return AdfToMarkdown.with(MarkdownOptions.defaults().withHtmlVisualMarks(true))
        .toMarkdown(json);
  }

  private static String marked(String mark) {
    return doc(paragraph(text("x", mark)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "color:red; position:fixed",
        "red;position:fixed",
        "red position:fixed",
        "url(http://evil/x.png)",
        "url(x)",
        "expression(alert(1))",
        "var(--x)",
        "red}body{display:none",
        "red\\;",
        "red\"><script>",
        "'red'",
        "/*x*/red",
        "-3",
        "#12",
        "#gggggg",
        "red\nblue",
      })
  void an_unsafe_css_value_drops_its_declaration(String value) {
    var json = quote(value);

    assertThat(visual(marked(mark("textColor", "{\"color\":" + json + "}")))).isEqualTo("x");
    assertThat(visual(marked(mark("backgroundColor", "{\"color\":" + json + "}")))).isEqualTo("x");
    assertThat(visual(marked(mark("fontSize", "{\"fontSize\":" + json + "}")))).isEqualTo("x");
    assertThat(visual(marked(mark("border", "{\"size\":\"1\",\"color\":" + json + "}"))))
        .isEqualTo("x");
    assertThat(visual(marked(mark("border", "{\"color\":\"#000\",\"size\":" + json + "}"))))
        .isEqualTo("x");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "#f00",
        "#FF0000",
        "#091E4224",
        "red",
        "transparent",
        "x-small",
        "rgb(1, 2, 3)",
        "rgba(0,0,0,0.5)",
        "hsl(120, 100%, 50%)",
        "20px",
        "1.5em",
        "120%",
        "0",
      })
  void a_safe_css_value_is_kept_verbatim(String value) {
    assertThat(visual(marked(mark("textColor", "{\"color\":" + quote(value) + "}"))))
        .isEqualTo("<span style=\"color:" + value + "\">x</span>");
    assertThat(visual(marked(mark("fontSize", "{\"fontSize\":" + quote(value) + "}"))))
        .isEqualTo("<span style=\"font-size:" + value + "\">x</span>");
  }

  @Test
  void one_unsafe_declaration_leaves_the_safe_ones_in_the_span() {
    var markdown =
        visual(
            marked(
                mark("textColor", "{\"color\":\"red;top:0\"}")
                    + ","
                    + mark("backgroundColor", "{\"color\":\"#eee\"}")));

    assertThat(markdown).isEqualTo("<span style=\"background-color:#eee\">x</span>");
  }

  @Test
  void a_safe_border_keeps_its_shape() {
    assertThat(visual(marked(mark("border", "{\"size\":2,\"color\":\"#000000\"}"))))
        .isEqualTo("<span style=\"border:2px solid #000000\">x</span>");
  }

  @Test
  void the_first_indentation_that_indents_wins_like_alignment() {
    var noOp = mark("indentation", "{\"level\":0}");
    var negative = mark("indentation", "{\"level\":-2}");
    var two = mark("indentation", "{\"level\":2}");
    var one = mark("indentation", "{\"level\":1}");

    var markdown =
        AdfToMarkdown.create()
            .toMarkdown(
                doc(markedParagraph(noOp + "," + negative + "," + two + "," + one, text("x"))));

    assertThat(markdown).isEqualTo("\u00a0".repeat(8) + "x");
    assertThat(
            AdfToMarkdown.create()
                .toMarkdown(
                    doc(
                        node("heading", "{\"level\":1}", text("h"))
                            .replace(
                                "\"content\"",
                                "\"marks\":[" + noOp + "," + one + "],\"content\""))))
        .isEqualTo("# " + "\u00a0".repeat(4) + "h");
  }

  @Test
  void a_noop_alignment_does_not_hide_a_later_one() {
    var markdown =
        visual(
            doc(
                markedParagraph(
                    mark("alignment", "{\"align\":\"start\"}")
                        + ","
                        + mark("alignment", "{\"align\":\"end\"}"),
                    text("x"))));

    assertThat(markdown).isEqualTo("<div align=\"right\">\n\nx\n\n</div>");
  }
}
