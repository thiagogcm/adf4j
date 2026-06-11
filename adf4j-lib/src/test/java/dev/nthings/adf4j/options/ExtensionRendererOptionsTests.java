package dev.nthings.adf4j.options;

import java.util.List;

import dev.nthings.adf4j.AdfToMarkdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionRendererOptionsTests {

  // A block extension for a non-Confluence macro carrying one macro param.
  private static final String BLOCK_EXTENSION =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.acme.macros",
              "extensionKey": "jira-issue",
              "parameters": { "macroParams": { "key": { "value": "PROJ-42" } } }
            }
          }
        ]
      }
      """;

  // An inline extension for the same macro, sitting inside a paragraph.
  private static final String INLINE_EXTENSION =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              { "type": "text", "text": "See " },
              {
                "type": "inlineExtension",
                "attrs": {
                  "extensionType": "com.acme.macros",
                  "extensionKey": "jira-issue",
                  "parameters": { "macroParams": { "key": { "value": "PROJ-7" } } }
                }
              }
            ]
          }
        ]
      }
      """;

  private static ExtensionRenderer jiraIssue(String body) {
    return extension -> {
      if ("com.acme.macros".equals(extension.extensionType())
          && "jira-issue".equals(extension.extensionKey())) {
        return body.formatted(extension.parameter("key"));
      }
      return null;
    };
  }

  @Test
  void without_a_renderer_an_unknown_extension_falls_back_to_a_placeholder() {
    var markdown = AdfToMarkdown.create().toMarkdown(BLOCK_EXTENSION).strip();

    assertThat(markdown).contains("Extension");
  }

  @Test
  void a_custom_renderer_renders_a_block_extension() {
    var options =
        MarkdownOptions.defaults()
            .withExtensionRenderers(List.of(jiraIssue("[%1$s](https://jira.example.com/browse/%1$s)")));
    var markdown = AdfToMarkdown.with(options).toMarkdown(BLOCK_EXTENSION).strip();

    assertThat(markdown).isEqualTo("[PROJ-42](https://jira.example.com/browse/PROJ-42)");
  }

  @Test
  void a_custom_renderer_renders_an_inline_extension() {
    var options = MarkdownOptions.defaults().withExtensionRenderers(List.of(jiraIssue("#%s")));
    var markdown = AdfToMarkdown.with(options).toMarkdown(INLINE_EXTENSION).strip();

    assertThat(markdown).isEqualTo("See #PROJ-7");
  }

  @Test
  void a_renderer_that_defers_falls_through_to_the_default() {
    ExtensionRenderer deferring = extension -> null;
    var options = MarkdownOptions.defaults().withExtensionRenderers(List.of(deferring));
    var markdown = AdfToMarkdown.with(options).toMarkdown(BLOCK_EXTENSION).strip();

    assertThat(markdown).contains("Extension");
  }

  @Test
  void a_renderer_that_throws_is_contained_and_falls_through_to_the_default() {
    ExtensionRenderer throwing = extension -> {
      throw new RuntimeException("boom");
    };
    var options = MarkdownOptions.defaults().withExtensionRenderers(List.of(throwing));
    var markdown = AdfToMarkdown.with(options).toMarkdown(BLOCK_EXTENSION).strip();

    assertThat(markdown).contains("Extension");
  }

  @Test
  void renderers_are_consulted_in_order_and_the_first_match_wins() {
    var options =
        MarkdownOptions.defaults()
            .withExtensionRenderers(List.of(jiraIssue("first:%s"), jiraIssue("second:%s")));
    var markdown = AdfToMarkdown.with(options).toMarkdown(BLOCK_EXTENSION).strip();

    assertThat(markdown).isEqualTo("first:PROJ-42");
  }
}
