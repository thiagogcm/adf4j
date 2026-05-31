package dev.nthings.adf4j;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AdfToMarkdownTests {

  private static final AdfToMarkdown PROCESSOR = AdfToMarkdown.create();

  @Test
  void default_construction_exposes_the_public_parse_and_render_workflow() {
    var markdown = PROCESSOR.toMarkdown(
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
                      "text": "Hello, ADF"
                    }
                  ]
                }
              ]
            }
            """);

    assertThat(markdown).isEqualTo("Hello, ADF");
    assertThat(PROCESSOR.parse("{\"type\":\"doc\",\"version\":1,\"content\":[]}").validAdfRoot())
        .isTrue();
  }

  @Test
  void with_rejects_null_options() {
    assertThatNullPointerException()
        .isThrownBy(() -> AdfToMarkdown.with(null))
        .withMessage("options");
  }
}
