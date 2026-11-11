package dev.nthings.adf4j.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.options.ConversionOptions;
import org.junit.jupiter.api.Test;

/// Markdown syntax edge cases: escaping at line starts, hard breaks, cards, nested lists, link
/// titles, code fences and sub/superscript.
class MarkdownSyntaxTests {

  private static final AdfToMarkdown processor = AdfToMarkdown.create();

  @Test
  void convert_renders_card_variants_without_losing_identity_or_escaping() {
    // Cases mirror docs/spec card shapes; the last embedCard omits its required url to pin the
    // malformed fallback instead of throwing on bad producer data.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "paragraph",
              "content": [
                { "type": "text", "text": "Inline " },
                { "type": "inlineCard", "attrs": { "url": "https://example.com/ticket/123" } }
              ]
            },
            {
              "type": "blockCard",
              "attrs": { "url": "/wiki/spaces/DEV/pages/123" }
            },
            {
              "type": "blockCard",
              "attrs": {
                "data": {
                  "@type": "Object",
                  "name": "Dashboard *42*",
                  "url": "https://example.com/dashboards/42"
                }
              }
            },
            {
              "type": "blockCard",
              "attrs": {
                "datasource": {
                  "id": "source-1",
                  "parameters": {},
                  "views": [{ "type": "table" }]
                }
              }
            },
            {
              "type": "embedCard",
              "attrs": { "layout": "center" }
            }
          ]
        }
        """;

    var markdown = processor.toMarkdown(adf);

    assertThat(markdown)
        .isEqualToNormalizingNewlines(
            """
            Inline <https://example.com/ticket/123>

            [/wiki/spaces/DEV/pages/123](/wiki/spaces/DEV/pages/123)

            [Dashboard \\*42\\*](https://example.com/dashboards/42)

            \\[Card: source-1\\]

            \\[Embed card\\]
            """
                .strip());
  }

  @Test
  void convert_escapes_text_after_hard_break_as_a_new_line_start() {
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "paragraph",
              "content": [
                { "type": "text", "text": "alpha" },
                { "type": "hardBreak" },
                { "type": "text", "text": "# beta" }
              ]
            }
          ]
        }
        """;

    var markdown = processor.toMarkdown(adf);

    assertThat(markdown).isEqualToNormalizingNewlines("alpha  \n\\# beta");
  }

  @Test
  void collapse_hard_breaks_renders_a_soft_break_without_trailing_spaces() {
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "paragraph",
              "content": [
                { "type": "text", "text": "alpha" },
                { "type": "hardBreak" },
                { "type": "text", "text": "# beta" }
              ]
            }
          ]
        }
        """;

    var collapsing = AdfToMarkdown.with(ConversionOptions.defaults().withCollapseHardBreaks(true));
    var markdown = collapsing.toMarkdown(adf);

    assertThat(markdown).isEqualToNormalizingNewlines("alpha\n\\# beta");
    assertThat(markdown)
        .isEqualTo(
            """
            alpha
            \\# beta""");
  }

  @Test
  void convert_keeps_a_nested_ordered_list_that_starts_above_one() {
    // A start != 1 sublist must get the blank line CommonMark needs, or it folds into the
    // paragraph.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "bulletList",
              "content": [
                {
                  "type": "listItem",
                  "content": [
                    { "type": "paragraph", "content": [{ "type": "text", "text": "top" }] },
                    {
                      "type": "orderedList",
                      "attrs": { "order": 3 },
                      "content": [
                        {
                          "type": "listItem",
                          "content": [
                            { "type": "paragraph", "content": [{ "type": "text", "text": "n1" }] }
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;

    assertThat(processor.toMarkdown(adf)).isEqualToNormalizingNewlines("- top\n\n  3. n1");
  }

  @Test
  void convert_collapses_a_link_title_newline_to_a_space() {
    var adf =
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
                  "text": "x",
                  "marks": [{ "type": "link", "attrs": { "href": "https://e.com", "title": "a\\nb" } }]
                }
              ]
            }
          ]
        }
        """;

    assertThat(processor.toMarkdown(adf)).isEqualTo("[x](https://e.com \"a b\")");
  }

  @Test
  void convert_trims_whitespace_from_a_code_block_language() {
    assertThat(processor.toMarkdown(codeBlock("  js  ")))
        .isEqualTo(
            """
            ```js
            code
            ```""");
  }

  @Test
  void convert_drops_backticks_from_a_code_block_language() {
    assertThat(processor.toMarkdown(codeBlock("a`b`c")))
        .isEqualTo(
            """
            ```abc
            code
            ```""");
  }

  @Test
  void convert_leaves_an_unknown_subsup_subtype_unwrapped() {
    assertThat(processor.toMarkdown(subSupMark("foo"))).isEqualTo("x");
    assertThat(processor.toMarkdown(subSupMark("sub"))).isEqualTo("<sub>x</sub>");
  }

  private static String codeBlock(String language) {
    return """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "codeBlock",
              "attrs": { "language": "%s" },
              "content": [{ "type": "text", "text": "code" }]
            }
          ]
        }
        """
        .formatted(language);
  }

  private static String subSupMark(String type) {
    return """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "paragraph",
              "content": [
                {
                  "type": "text",
                  "text": "x",
                  "marks": [{ "type": "subsup", "attrs": { "type": "%s" } }]
                }
              ]
            }
          ]
        }
        """
        .formatted(type);
  }
}
