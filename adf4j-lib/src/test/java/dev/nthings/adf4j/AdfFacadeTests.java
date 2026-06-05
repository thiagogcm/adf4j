package dev.nthings.adf4j;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdfFacadeTests {

  @Test
  void to_markdown_matches_the_default_processor_for_valid_adf() {
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "paragraph",
              "content": [
                { "type": "text", "text": "See " },
                { "type": "inlineCard", "attrs": { "url": "https://example.com/ticket/123" } }
              ]
            }
          ]
        }
        """;

    assertThat(Adf.toMarkdown(adf)).isEqualTo(AdfToMarkdown.create().toMarkdown(adf));
  }

  @Test
  void to_markdown_keeps_the_default_unknown_node_policy() {
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            { "type": "futureBlock", "attrs": { "source": "spec-drift" } }
          ]
        }
        """;

    assertThat(Adf.toMarkdown(adf)).isEqualTo("\\[Unsupported: futureBlock\\]");
  }
}
