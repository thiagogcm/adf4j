package dev.nthings.adf4j.options;

import static dev.nthings.adf4j.testing.AdfSnippets.doc;
import static dev.nthings.adf4j.testing.AdfSnippets.macro;
import static dev.nthings.adf4j.testing.AdfSnippets.paragraph;
import static dev.nthings.adf4j.testing.AdfSnippets.text;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtensionRendererOptionsTests {

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
        return ExtensionOutput.replace(body.formatted(extension.macroParams().value("key")));
      }
      return ExtensionOutput.defer();
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
        ConversionOptions.defaults()
            .withExtensionRenderers(
                List.of(jiraIssue("[%1$s](https://jira.example.com/browse/%1$s)")));
    var markdown = AdfToMarkdown.with(options).toMarkdown(BLOCK_EXTENSION).strip();

    assertThat(markdown).isEqualTo("[PROJ-42](https://jira.example.com/browse/PROJ-42)");
  }

  @Test
  void a_custom_renderer_renders_an_inline_extension() {
    var options = ConversionOptions.defaults().withExtensionRenderers(List.of(jiraIssue("#%s")));
    var markdown = AdfToMarkdown.with(options).toMarkdown(INLINE_EXTENSION).strip();

    assertThat(markdown).isEqualTo("See #PROJ-7");
  }

  @Test
  void a_renderer_that_defers_falls_through_to_the_default() {
    ExtensionRenderer deferring = extension -> ExtensionOutput.defer();
    var options = ConversionOptions.defaults().withExtensionRenderers(List.of(deferring));
    var markdown = AdfToMarkdown.with(options).toMarkdown(BLOCK_EXTENSION).strip();

    assertThat(markdown).contains("Extension");
  }

  @Test
  void a_renderer_that_throws_is_contained_and_falls_through_to_the_default() {
    ExtensionRenderer throwing =
        extension -> {
          throw new RuntimeException("boom");
        };
    var options = ConversionOptions.defaults().withExtensionRenderers(List.of(throwing));
    var markdown = AdfToMarkdown.with(options).toMarkdown(BLOCK_EXTENSION).strip();

    assertThat(markdown).contains("Extension");
  }

  @Test
  void renderers_are_consulted_in_order_and_the_first_match_wins() {
    var options =
        ConversionOptions.defaults()
            .withExtensionRenderers(List.of(jiraIssue("first:%s"), jiraIssue("second:%s")));
    var markdown = AdfToMarkdown.with(options).toMarkdown(BLOCK_EXTENSION).strip();

    assertThat(markdown).isEqualTo("first:PROJ-42");
  }

  @Test
  void a_custom_renderer_sees_a_bodied_excerpt_and_the_engine_still_renders_its_body() {
    var excerpt = doc(macro("bodiedExtension", "excerpt", "{}", paragraph(text("Body"))));
    var options =
        ConversionOptions.defaults()
            .withExtensionRenderers(
                List.of(
                    node ->
                        "excerpt".equals(node.extensionKey())
                            ? ExtensionOutput.header("Header")
                            : ExtensionOutput.defer()));

    assertThat(AdfToMarkdown.with(options).toMarkdown(excerpt)).isEqualTo("Header\n\nBody");
  }

  @Test
  void a_bodied_excerpt_stays_transparent_when_every_renderer_declines() {
    var excerpt = doc(macro("bodiedExtension", "excerpt", "{}", paragraph(text("Body"))));
    var options =
        ConversionOptions.defaults().withExtensionRenderers(List.of(_ -> ExtensionOutput.defer()));

    assertThat(AdfToMarkdown.with(options).toMarkdown(excerpt)).isEqualTo("Body");
  }
}
