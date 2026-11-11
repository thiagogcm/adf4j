package dev.nthings.adf4j;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.options.ConversionOptions;
import org.junit.jupiter.api.Test;

class ExcerptMetadataTests {

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
    var metadata = AdfToMarkdown.create().analyze(AdfJson.parse(PAGE_WITH_INCLUDES)).metadata();

    assertThat(metadata.excerptRefs()).hasSize(2);
    assertThat(metadata.excerptRefs().get(0).page()).isEqualTo("Source Page");
    assertThat(metadata.excerptRefs().get(0).excerptName()).isNull();
    assertThat(metadata.excerptRefs().get(1).excerptName()).isEqualTo("Known Issues");
    // Title-based references stay out of pageIds, which carries page node ids.
    assertThat(metadata.pageIds()).isEmpty();
  }

  @Test
  void an_excerpt_definition_exposes_its_name_and_marked_region() {
    var metadata =
        AdfToMarkdown.create().analyze(AdfJson.parse(SOURCE_PAGE_WITH_EXCERPT)).metadata();

    assertThat(metadata.excerpts())
        .singleElement()
        .satisfies(
            excerpt -> {
              assertThat(excerpt.name()).isEqualTo("Known Issues");
              assertThat(excerpt.content()).hasSize(1);
            });
  }

  @Test
  void an_unnamed_excerpt_definition_has_a_null_name() {
    var metadata = AdfToMarkdown.create().analyze(AdfJson.parse(UNNAMED_EXCERPT)).metadata();

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
    // Documented recipe: index excerpts, render one standalone, answer includes elsewhere.
    var converter = AdfToMarkdown.create();
    var excerpt =
        converter.analyze(AdfJson.parse(SOURCE_PAGE_WITH_EXCERPT)).metadata().excerpts().getFirst();
    var fragmentMarkdown = converter.convert(excerpt).body();

    var options =
        ConversionOptions.defaults()
            .withExcerptResolver(
                reference ->
                    "Known Issues".equals(reference.excerptName()) ? fragmentMarkdown : null);
    var result = AdfToMarkdown.with(options).convert(PAGE_WITH_INCLUDES);

    assertThat(fragmentMarkdown).isEqualTo("shared \\*fragment\\*");
    assertThat(result.body()).contains("shared \\*fragment\\*");
    // The unnamed include declined and stays trackable; the named one resolved.
    assertThat(result.unresolved()).hasSize(1);
  }

  @Test
  void converting_a_definition_ignores_the_document_title() {
    var options = ConversionOptions.defaults().withDocumentTitle("Source page");
    var converter = AdfToMarkdown.with(options);
    var parsed = AdfJson.parse(SOURCE_PAGE_WITH_EXCERPT);
    var excerpt = converter.analyze(parsed).metadata().excerpts().getFirst();

    assertThat(converter.convert(excerpt).body()).isEqualTo("shared \\*fragment\\*");
    // The title still belongs to the whole page.
    assertThat(converter.convert(parsed).body()).startsWith("# Source page");
  }
}
