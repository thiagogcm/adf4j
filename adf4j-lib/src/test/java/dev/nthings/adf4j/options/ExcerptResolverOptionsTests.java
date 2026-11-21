package dev.nthings.adf4j.options;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.Adf;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

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
    var options =
        ConversionOptions.defaults().withExcerptResolver(reference -> "**Shared** notice body");

    var result = AdfToMarkdown.with(options).convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(result.body()).isEqualTo("**Shared** notice body");
    assertThat(result.unresolved().isEmpty()).isTrue();
    assertThat(result.diagnostics()).noneMatch(d -> d.code() == Diagnostic.Code.UNSUPPORTED_MACRO);
  }

  @Test
  void a_resolver_sees_the_page_name_and_parameters() {
    var seen = new ExcerptIncludeReference[1];
    var options =
        ConversionOptions.defaults()
            .withExcerptResolver(
                reference -> {
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
    var options =
        ConversionOptions.defaults()
            .withExcerptResolver(
                reference ->
                    "Problemas Conhecidos".equals(reference.excerptName()) ? "none known" : null);

    var result = AdfToMarkdown.with(options).convert(INLINE_NAMED_EXCERPT_INCLUDE);

    assertThat(result.body()).isEqualTo("Status: none known");
  }

  @Test
  void without_a_resolver_the_macro_keeps_a_placeholder_and_is_reported_unresolved() {
    var result = AdfToMarkdown.create().convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(result.body()).isEqualTo("\\[Excerpt include: Shared Notices\\]");
    assertThat(result.unresolved())
        .singleElement()
        .satisfies(reference -> assertThat(reference.id()).isEqualTo("Shared Notices"));
    assertThat(result.diagnostics()).noneMatch(d -> d.code() == Diagnostic.Code.UNSUPPORTED_MACRO);
  }

  @Test
  void a_named_include_placeholder_names_the_excerpt() {
    var result = AdfToMarkdown.create().convert(INLINE_NAMED_EXCERPT_INCLUDE);

    assertThat(result.body())
        .isEqualTo("Status: \\[Excerpt include: Dados Cadastrais / Problemas Conhecidos\\]");
    assertThat(result.unresolved())
        .singleElement()
        .satisfies(
            reference -> {
              assertThat(reference.id()).isEqualTo("Dados Cadastrais");
              assertThat(reference.fallback())
                  .isEqualTo("\\[Excerpt include: Dados Cadastrais / Problemas Conhecidos\\]");
            });
  }

  @Test
  void a_declining_resolver_keeps_the_placeholder_and_records_the_reference() {
    var options = ConversionOptions.defaults().withExcerptResolver(reference -> null);

    var result = AdfToMarkdown.with(options).convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(result.body()).isEqualTo("\\[Excerpt include: Shared Notices\\]");
    assertThat(result.unresolved()).hasSize(1);
  }

  @Test
  void a_throwing_resolver_counts_as_declined() {
    var options =
        ConversionOptions.defaults()
            .withExcerptResolver(
                reference -> {
                  throw new IllegalStateException("index unavailable");
                });

    var result = AdfToMarkdown.with(options).convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(result.body()).isEqualTo("\\[Excerpt include: Shared Notices\\]");
    assertThat(result.unresolved()).hasSize(1);
  }

  @Test
  void an_empty_string_answer_suppresses_the_macro_and_counts_as_resolved() {
    var options = ConversionOptions.defaults().withExcerptResolver(reference -> "");

    var result = AdfToMarkdown.with(options).convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(result.body()).isEmpty();
    assertThat(result.unresolved().isEmpty()).isTrue();
  }

  @Test
  void an_include_without_a_source_page_falls_back_to_the_generic_placeholder() {
    var options = ConversionOptions.defaults().withExcerptResolver(reference -> "resolved");

    var result = AdfToMarkdown.with(options).convert(MALFORMED_EXCERPT_INCLUDE);

    assertThat(result.body())
        .isEqualTo("\\[Extension: com.atlassian.confluence.macro.core/excerpt-include\\]");
    assertThat(result.unresolved()).isEmpty();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.code() == Diagnostic.Code.UNSUPPORTED_MACRO
                    && d.severity() == Diagnostic.Severity.WARNING);
  }

  @Test
  void an_ast_excerpt_content_resolver_renders_blocks_natively() {
    var definition =
        new ExcerptDefinition(
            null,
            List.of(
                Adf.paragraph(Adf.text("Shared", Adf.strong()), Adf.text(" notice")),
                Adf.bulletList(Adf.listItem("Item one"))));
    var options = ConversionOptions.defaults().withExcerptContentResolver(ref -> definition);

    var result = AdfToMarkdown.with(options).convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(result.body()).isEqualTo("**Shared** notice\n\n- Item one");
    assertThat(result.unresolved()).isEmpty();
  }

  @Test
  void an_ast_excerpt_content_resolver_renders_single_paragraph_inline() {
    var definition =
        new ExcerptDefinition(
            "Problemas Conhecidos", List.of(Adf.paragraph(Adf.text("none", Adf.em()))));
    var options =
        ConversionOptions.defaults()
            .withExcerptContentResolver(
                ref -> "Problemas Conhecidos".equals(ref.excerptName()) ? definition : null);

    var result = AdfToMarkdown.with(options).convert(INLINE_NAMED_EXCERPT_INCLUDE);

    assertThat(result.body()).isEqualTo("Status: *none*");
    assertThat(result.unresolved()).isEmpty();
  }

  @Test
  void an_empty_ast_excerpt_suppresses_output_and_null_falls_through_to_markdown_resolver() {
    var empty =
        ConversionOptions.defaults()
            .withExcerptContentResolver(ref -> new ExcerptDefinition(null, List.of()));
    var emptyResult = AdfToMarkdown.with(empty).convert(BLOCK_EXCERPT_INCLUDE);
    assertThat(emptyResult.body()).isEmpty();
    assertThat(emptyResult.unresolved()).isEmpty();

    var fallback =
        ConversionOptions.defaults()
            .withExcerptContentResolver(ref -> null)
            .withExcerptResolver(ref -> "from-markdown");
    var fallbackResult = AdfToMarkdown.with(fallback).convert(BLOCK_EXCERPT_INCLUDE);
    assertThat(fallbackResult.body()).isEqualTo("from-markdown");
  }

  @Test
  void a_cyclic_ast_excerpt_expands_once_and_falls_back_to_placeholder() {
    var recursiveInclude =
        Adf.extension(
            "com.atlassian.confluence.macro.core",
            "excerpt-include",
            Adf.macroParameters(Map.of("", "Self")));
    var definition = new ExcerptDefinition(null, List.of(recursiveInclude));
    var requested = new ArrayList<String>();
    var options =
        ConversionOptions.defaults()
            .withExcerptContentResolver(
                ref -> {
                  requested.add(ref.page());
                  return definition;
                });

    var result = AdfToMarkdown.with(options).convert(BLOCK_EXCERPT_INCLUDE);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.body()).isEqualTo("\\[Excerpt include: Self\\]");
    assertThat(result.unresolved()).hasSize(1);
    // The outer page and "Self" are each resolved once; the cycle is cut before a third lookup.
    assertThat(requested).hasSize(2).doesNotHaveDuplicates();
  }

  @Test
  void repeated_includes_of_one_excerpt_resolve_once_per_render() {
    var include =
        Adf.extension(
            "com.atlassian.confluence.macro.core",
            "excerpt-include",
            Adf.macroParameters(Map.of("", "Shared")));
    var calls = new AtomicInteger();
    var options =
        ConversionOptions.defaults()
            .withExcerptContentResolver(
                ref -> {
                  calls.incrementAndGet();
                  return new ExcerptDefinition(null, List.of(Adf.paragraph("Notice")));
                });

    var result = AdfToMarkdown.with(options).convert(Adf.doc(include, include));

    assertThat(result.body()).isEqualTo("Notice\n\nNotice");
    assertThat(calls).hasValue(1);
  }
}
