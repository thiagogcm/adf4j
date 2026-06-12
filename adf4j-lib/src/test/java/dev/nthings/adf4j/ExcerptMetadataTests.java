package dev.nthings.adf4j;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.options.MarkdownOptions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcerptMetadataTests {

  // A source page defining a named excerpt plus surrounding content.
  private static final String SOURCE_PAGE_WITH_EXCERPT =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          { "type": "paragraph", "content": [ { "type": "text", "text": "before" } ] },
          {
            "type": "bodiedExtension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "excerpt",
              "parameters": {
                "macroParams": {
                  "name": { "value": "Known Issues" },
                  "atlassian-macro-output-type": { "value": "BLOCK" }
                }
              }
            },
            "content": [
              { "type": "paragraph", "content": [ { "type": "text", "text": "shared *fragment*" } ] }
            ]
          },
          { "type": "paragraph", "content": [ { "type": "text", "text": "after" } ] }
        ]
      }
      """;

  private static final String UNNAMED_EXCERPT =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "bodiedExtension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "excerpt",
              "parameters": { "macroParams": {} }
            },
            "content": [
              { "type": "paragraph", "content": [ { "type": "text", "text": "unnamed region" } ] }
            ]
          }
        ]
      }
      """;

  private static final String PAGE_WITH_INCLUDES =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "excerpt-include",
              "parameters": { "macroParams": { "": { "value": "Source Page" } } }
            }
          },
          {
            "type": "paragraph",
            "content": [
              {
                "type": "inlineExtension",
                "attrs": {
                  "extensionType": "com.atlassian.confluence.macro.core",
                  "extensionKey": "excerpt-include",
                  "parameters": {
                    "macroParams": {
                      "": { "value": "Source Page" },
                      "name": { "value": "Known Issues" }
                    }
                  }
                }
              }
            ]
          }
        ]
      }
      """;

  @Test
  void excerpt_include_occurrences_are_listed_in_metadata() {
    var metadata = AdfToMarkdown.create().analyze(PAGE_WITH_INCLUDES);

    assertThat(metadata.excerptRefs()).hasSize(2);
    assertThat(metadata.excerptRefs().get(0).page()).isEqualTo("Source Page");
    assertThat(metadata.excerptRefs().get(0).excerptName()).isNull();
    assertThat(metadata.excerptRefs().get(1).excerptName()).isEqualTo("Known Issues");
    // Title-based references stay out of pageRefs, which carries page node ids.
    assertThat(metadata.pageRefs()).isEmpty();
  }

  @Test
  void an_excerpt_definition_exposes_its_name_and_marked_region() {
    var metadata = AdfToMarkdown.create().analyze(SOURCE_PAGE_WITH_EXCERPT);

    assertThat(metadata.excerpts())
        .singleElement()
        .satisfies(excerpt -> {
          assertThat(excerpt.name()).isEqualTo("Known Issues");
          assertThat(excerpt.content()).hasSize(1);
        });
  }

  @Test
  void an_unnamed_excerpt_definition_has_a_null_name() {
    var metadata = AdfToMarkdown.create().analyze(UNNAMED_EXCERPT);

    assertThat(metadata.excerpts())
        .singleElement()
        .satisfies(excerpt -> assertThat(excerpt.name()).isNull());
  }

  @Test
  void the_excerpt_body_still_renders_transparently_on_the_source_page() {
    var markdown = AdfToMarkdown.create().toMarkdown(SOURCE_PAGE_WITH_EXCERPT);

    assertThat(markdown).contains("shared \\*fragment\\*");
    assertThat(markdown).doesNotContain("Extension");
  }

  @Test
  void a_definition_converts_into_resolver_ready_markdown() {
    // The documented recipe: index the source page's excerpts, render one as its own document, and
    // answer an excerpt-include on another page with the result.
    var converter = AdfToMarkdown.create();
    var excerpt = converter.analyze(SOURCE_PAGE_WITH_EXCERPT).excerpts().getFirst();
    var fragmentMarkdown =
        converter.convert(new AdfDocument(1, excerpt.content())).body();

    var options = MarkdownOptions.defaults()
        .withExcerptResolver(reference ->
            "Known Issues".equals(reference.excerptName()) ? fragmentMarkdown : null);
    var result = AdfToMarkdown.with(options).convert(PAGE_WITH_INCLUDES);

    assertThat(fragmentMarkdown).isEqualTo("shared \\*fragment\\*");
    assertThat(result.body()).contains("shared \\*fragment\\*");
    // The unnamed include declined and stays trackable; the named one resolved.
    assertThat(result.unresolved().excerptRefs()).hasSize(1);
  }
}
