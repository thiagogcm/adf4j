package dev.nthings.adf4j.options;

import dev.nthings.adf4j.AdfToMarkdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EscapeParenthesesOptionsTests {

  private static final String PARAGRAPH_WITH_PARENS =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [ { "type": "text", "text": "See (a) and (b)." } ]
          }
        ]
      }
      """;

  // A status node followed by text that, unescaped, would read as a Markdown link.
  private static final String LINK_LOOKALIKE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [ { "type": "text", "text": "[Done](see ref)" } ]
          }
        ]
      }
      """;

  // A paragraph whose text starts with "1)" — a CommonMark ordered-list delimiter.
  private static final String LEADING_ORDERED_PAREN =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [ { "type": "text", "text": "1) not a list" } ]
          }
        ]
      }
      """;

  private static final String IMAGE_WITH_PARENS_ALT =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "mediaSingle",
            "attrs": { "layout": "center" },
            "content": [
              {
                "type": "media",
                "attrs": {
                  "type": "external",
                  "url": "https://example.com/chart.png",
                  "alt": "chart (v2)"
                }
              }
            ]
          }
        ]
      }
      """;

  @Test
  void parentheses_are_not_escaped_by_default() {
    var markdown = AdfToMarkdown.create().toMarkdown(PARAGRAPH_WITH_PARENS).strip();

    assertThat(markdown).isEqualTo("See (a) and (b).");
  }

  @Test
  void parentheses_are_escaped_when_opted_in() {
    var options = MarkdownOptions.defaults().withEscapeParentheses(true);

    var markdown = AdfToMarkdown.with(options).toMarkdown(PARAGRAPH_WITH_PARENS).strip();

    assertThat(markdown).isEqualTo("See \\(a\\) and \\(b\\).");
  }

  @Test
  void unescaped_parentheses_still_cannot_form_a_link_because_brackets_stay_escaped() {
    // The bracket escaping (not the paren escaping) is what defuses the link, so the default output
    // is still inert text rather than an anchor.
    var markdown = AdfToMarkdown.create().toMarkdown(LINK_LOOKALIKE).strip();

    assertThat(markdown).isEqualTo("\\[Done\\](see ref)");
  }

  @Test
  void a_leading_ordered_paren_marker_is_neutralised_even_when_parentheses_are_not_escaped() {
    // "1)" at column 0 would otherwise open an ordered list, so the delimiter is backslash-escaped
    // regardless of the parentheses setting.
    var defaultOutput = AdfToMarkdown.create().toMarkdown(LEADING_ORDERED_PAREN).strip();
    var escapedOutput = AdfToMarkdown.with(MarkdownOptions.defaults().withEscapeParentheses(true))
        .toMarkdown(LEADING_ORDERED_PAREN)
        .strip();

    assertThat(defaultOutput).isEqualTo("1\\) not a list");
    assertThat(escapedOutput).isEqualTo("1\\) not a list");
  }

  @Test
  void image_alt_text_parentheses_follow_the_same_toggle() {
    var defaultOutput = AdfToMarkdown.create().toMarkdown(IMAGE_WITH_PARENS_ALT).strip();
    var escapedOutput = AdfToMarkdown.with(MarkdownOptions.defaults().withEscapeParentheses(true))
        .toMarkdown(IMAGE_WITH_PARENS_ALT)
        .strip();

    assertThat(defaultOutput).isEqualTo("![chart (v2)](https://example.com/chart.png)");
    assertThat(escapedOutput).isEqualTo("![chart \\(v2\\)](https://example.com/chart.png)");
  }
}
