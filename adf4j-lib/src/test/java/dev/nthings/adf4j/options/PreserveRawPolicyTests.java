package dev.nthings.adf4j.options;

import dev.nthings.adf4j.AdfToMarkdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PreserveRawPolicyTests {

  private static final String UNKNOWN_BLOCK =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          { "type": "mysteryBlock", "attrs": { "foo": "bar" } }
        ]
      }
      """;

  private static final String UNKNOWN_INLINE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              { "type": "text", "text": "Before " },
              { "type": "mysteryInline", "attrs": { "x": "1" } }
            ]
          }
        ]
      }
      """;

  @Test
  void preserve_raw_emits_an_unknown_block_as_a_fenced_json_block() {
    var options = MarkdownOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.PRESERVE_RAW);

    var markdown = AdfToMarkdown.with(options).toMarkdown(UNKNOWN_BLOCK).strip();

    assertThat(markdown)
        .isEqualTo("```json\n{\"type\":\"mysteryBlock\",\"attrs\":{\"foo\":\"bar\"}}\n```");
  }

  @Test
  void preserve_raw_emits_an_unknown_inline_as_a_code_span() {
    var options = MarkdownOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.PRESERVE_RAW);

    var markdown = AdfToMarkdown.with(options).toMarkdown(UNKNOWN_INLINE).strip();

    assertThat(markdown).isEqualTo("Before `{\"type\":\"mysteryInline\",\"attrs\":{\"x\":\"1\"}}`");
  }
}
