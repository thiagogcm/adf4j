package dev.nthings.adf4j.options;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.result.Diagnostic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcerptResolverOptionsTests {

  // A block-level excerpt-include targeting the unnamed excerpt of another page.
  private static final String BLOCK_EXCERPT_INCLUDE =
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
              "parameters": {
                "macroParams": {
                  "": { "value": "Shared Notices" },
                  "nopanel": { "value": "true" }
                }
              }
            }
          }
        ]
      }
      """;

  // The shape observed in the wild: an inline extension targeting a named excerpt.
  private static final String INLINE_NAMED_EXCERPT_INCLUDE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              { "type": "text", "text": "Status: " },
              {
                "type": "inlineExtension",
                "attrs": {
                  "extensionType": "com.atlassian.confluence.macro.core",
                  "extensionKey": "excerpt-include",
                  "parameters": {
                    "macroParams": {
                      "": { "value": "Dados Cadastrais" },
                      "name": { "value": "Problemas Conhecidos" },
                      "nopanel": { "value": "true" }
                    }
                  }
                }
              }
            ]
          }
        ]
      }
      """;

  // No source page in the default parameter: not a usable reference.
  private static final String MALFORMED_EXCERPT_INCLUDE =
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
              "parameters": { "macroParams": { "nopanel": { "value": "true" } } }
            }
          }
        ]
      }
      """;

  @Test
  void a_resolver_renders_the_excerpt_markdown_in_place() {
    var options = MarkdownOptions.defaults()
        .withExcerptResolver(reference -> "**Shared** notice body");

    var result = AdfToMarkdown.with(options).convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(result.body()).contains("**Shared** notice body");
    assertThat(result.unresolved().isEmpty()).isTrue();
    assertThat(result.diagnostics()).noneMatch(d -> "UNSUPPORTED_MACRO".equals(d.code()));
  }

  @Test
  void a_resolver_sees_the_page_name_and_parameters() {
    var seen = new ExcerptIncludeReference[1];
    var options = MarkdownOptions.defaults()
        .withExcerptResolver(reference -> {
          seen[0] = reference;
          return "resolved";
        });

    AdfToMarkdown.with(options).convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(seen[0].page()).isEqualTo("Shared Notices");
    assertThat(seen[0].excerptName()).isNull();
    assertThat(seen[0].parameters()).containsEntry("nopanel", "true");
  }

  @Test
  void an_inline_named_include_resolves_inline_and_carries_the_excerpt_name() {
    var options = MarkdownOptions.defaults()
        .withExcerptResolver(reference ->
            "Problemas Conhecidos".equals(reference.excerptName()) ? "none known" : null);

    var result = AdfToMarkdown.with(options).convert(INLINE_NAMED_EXCERPT_INCLUDE);

    assertThat(result.body()).contains("Status: none known");
  }

  @Test
  void without_a_resolver_the_macro_keeps_a_placeholder_and_is_reported_unresolved() {
    var result = AdfToMarkdown.create().convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(result.body()).contains("\\[Excerpt include: Shared Notices\\]");
    assertThat(result.unresolved().excerptRefs())
        .singleElement()
        .satisfies(reference -> assertThat(reference.page()).isEqualTo("Shared Notices"));
    assertThat(result.diagnostics()).noneMatch(d -> "UNSUPPORTED_MACRO".equals(d.code()));
  }

  @Test
  void a_named_include_placeholder_names_the_excerpt() {
    var result = AdfToMarkdown.create().convert(INLINE_NAMED_EXCERPT_INCLUDE);

    assertThat(result.body())
        .contains("\\[Excerpt include: Dados Cadastrais / Problemas Conhecidos\\]");
    assertThat(result.unresolved().excerptRefs())
        .singleElement()
        .satisfies(reference -> {
          assertThat(reference.page()).isEqualTo("Dados Cadastrais");
          assertThat(reference.excerptName()).isEqualTo("Problemas Conhecidos");
        });
  }

  @Test
  void a_declining_resolver_keeps_the_placeholder_and_records_the_reference() {
    var options = MarkdownOptions.defaults().withExcerptResolver(reference -> null);

    var result = AdfToMarkdown.with(options).convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(result.body()).contains("\\[Excerpt include: Shared Notices\\]");
    assertThat(result.unresolved().excerptRefs()).hasSize(1);
  }

  @Test
  void a_throwing_resolver_counts_as_declined() {
    var options = MarkdownOptions.defaults()
        .withExcerptResolver(reference -> {
          throw new IllegalStateException("index unavailable");
        });

    var result = AdfToMarkdown.with(options).convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(result.body()).contains("\\[Excerpt include: Shared Notices\\]");
    assertThat(result.unresolved().excerptRefs()).hasSize(1);
  }

  @Test
  void an_empty_string_answer_suppresses_the_macro_and_counts_as_resolved() {
    var options = MarkdownOptions.defaults().withExcerptResolver(reference -> "");

    var result = AdfToMarkdown.with(options).convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(result.body()).isEmpty();
    assertThat(result.unresolved().isEmpty()).isTrue();
  }

  @Test
  void an_include_without_a_source_page_falls_back_to_the_generic_placeholder() {
    var options = MarkdownOptions.defaults().withExcerptResolver(reference -> "resolved");

    var result = AdfToMarkdown.with(options).convert(MALFORMED_EXCERPT_INCLUDE);

    assertThat(result.body())
        .contains("\\[Extension: com.atlassian.confluence.macro.core/excerpt-include\\]");
    assertThat(result.unresolved().excerptRefs()).isEmpty();
    assertThat(result.diagnostics())
        .anyMatch(d -> "UNSUPPORTED_MACRO".equals(d.code())
            && d.severity() == Diagnostic.Severity.WARNING);
  }
}
