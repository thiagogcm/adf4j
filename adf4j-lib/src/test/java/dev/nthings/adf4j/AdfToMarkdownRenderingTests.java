package dev.nthings.adf4j;

import java.util.List;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.UnknownNodePolicy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdfToMarkdownRenderingTests {

  private final AdfTestSupport testSupport = AdfTestSupport.create();
  private final AdfToMarkdown processor = testSupport.processor();

  @Test
  void convert_returns_empty_metadata_for_blank_input() {
    var result = processor.convert("   ");

    assertThat(result.body()).isEmpty();
    assertThat(result.metadata()).isEqualTo(ContentMetadata.empty());
  }

  @Test
  void convert_returns_empty_result_with_diagnostics_for_invalid_adf_roots() {
    var rawPayload = "{\"type\":\"paragraph\",\"version\":1,\"content\":[]}";

    var result = processor.convert(rawPayload);

    assertThat(result.body()).isEmpty();
    assertThat(result.metadata()).isEqualTo(ContentMetadata.empty());
    assertThat(result.diagnostics()).isNotEmpty();
  }

  @Test
  void convert_matches_the_reporte_regression_case() throws Exception {
    var result = processor.convert(testSupport.caseInput("reporte"));

    assertThat(result.body())
        .isEqualToNormalizingNewlines(testSupport.caseOutput("reporte", ".md"))
        .doesNotContain("<table")
        .doesNotContain("<img")
        .doesNotContain("<span style=")
        .doesNotContain("<p style=");
  }

  @Test
  void convert_resolves_viewpdf_macros_with_attachment_context() throws Exception {
    var rawPayload = testSupport.caseInput("viewpdf-macros");
    var options = MarkdownOptions.defaults()
        .withContext(
            ConfluenceRenderContext.empty()
                .withAttachmentReferences(
                    List.of(new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf"))));

    var result = AdfToMarkdown.with(options).convert(rawPayload);

    assertThat(result.body()).isEqualTo("[PDF: guide.pdf](attachment:file-pdf-1)");
    assertThat(result.metadata().attachmentRefs())
        .singleElement()
        .isEqualTo(new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf"));
  }

  @Test
  void convert_applies_the_unknown_node_policy() throws Exception {
    var rawPayload = testSupport.caseInput("unknown-node-policy");

    assertThat(
        AdfToMarkdown.with(
                MarkdownOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.PLACEHOLDER))
            .toMarkdown(rawPayload))
        .contains("\\[Unsupported: mysteryBlock\\]");
    assertThat(
        AdfToMarkdown.with(MarkdownOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.SKIP))
            .toMarkdown(rawPayload))
        .isEmpty();
    assertThatThrownBy(
        () -> AdfToMarkdown.with(
                MarkdownOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.FAIL))
            .toMarkdown(rawPayload))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("mysteryBlock");
  }

  @Test
  void convert_supports_generic_options_without_context() throws Exception {
    var markdown = AdfToMarkdown.with(
            MarkdownOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.SKIP))
        .toMarkdown(testSupport.caseInput("unknown-node-policy"));

    assertThat(markdown).isEmpty();
  }

  @Test
  void convert_keeps_children_macros_as_placeholders_for_db_derived_cases() throws Exception {
    var markdown = processor.toMarkdown(testSupport.caseInput("especificacoes-reporte-children"));

    assertThat(markdown).contains("{{children}}").contains("Reporting Specifications");
  }

  @Test
  void convert_resolves_db_derived_viewpdf_cases_with_attachment_context() throws Exception {
    var options = MarkdownOptions.defaults()
        .withContext(
            ConfluenceRenderContext.empty()
                .withAttachmentReferences(
                    List.of(
                        new AttachmentReference(
                            "file-pdf-123",
                            "Open_Finance_cadastro_diretorio_passo_a_passo.pdf",
                            "application/pdf"))));

    var result = AdfToMarkdown.with(options).convert(testSupport.caseInput("lista-participantes-viewpdf"));

    assertThat(result.body())
        .contains("[PDF: Open_Finance_cadastro_diretorio_passo_a_passo.pdf](attachment:file-pdf-123)");
    assertThat(result.metadata().attachmentRefs())
        .singleElement()
        .satisfies(ref -> {
          assertThat(ref.fileId()).isEqualTo("file-pdf-123");
          assertThat(ref.title()).isEqualTo("Open_Finance_cadastro_diretorio_passo_a_passo.pdf");
        });
  }

  @Test
  void convert_renders_bodied_extension_header_then_body_and_escapes_marker_text() {
    // bodiedExtension requires a body in the local schema; unknown macro text is still
    // attribute-derived text and must not promote to a Markdown heading.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "bodiedExtension",
              "attrs": {
                "extensionType": "com.example.macros",
                "extensionKey": "callout",
                "text": "# Important"
              },
              "content": [
                { "type": "paragraph", "content": [{ "type": "text", "text": "Body text" }] }
              ]
            }
          ]
        }
        """;

    var markdown = processor.toMarkdown(adf);

    assertThat(markdown).isEqualToNormalizingNewlines("\\# Important\n\nBody text");
  }

  @Test
  void convert_renders_confluence_excerpt_bodied_extension_as_body_only() {
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "bodiedExtension",
              "attrs": {
                "extensionType": "com.atlassian.confluence.macro.core",
                "extensionKey": "excerpt",
                "text": "Excerpt wrapper"
              },
              "content": [
                { "type": "paragraph", "content": [{ "type": "text", "text": "Only this survives" }] }
              ]
            }
          ]
        }
        """;

    var markdown = processor.toMarkdown(adf);

    assertThat(markdown).isEqualTo("Only this survives");
  }

  @Test
  void convert_renders_bodied_sync_block_label_then_body() {
    // docs/spec/adf-schema.json requires resourceId/localId for sync blocks; the renderer should keep
    // the resource id visible and then salvage the block body.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "bodiedSyncBlock",
              "attrs": { "resourceId": "abc-123", "localId": "sync-1" },
              "content": [
                { "type": "paragraph", "content": [{ "type": "text", "text": "Synced paragraph" }] }
              ]
            }
          ]
        }
        """;

    var markdown = processor.toMarkdown(adf);

    assertThat(markdown).isEqualToNormalizingNewlines("\\[Sync block: abc-123\\]\n\nSynced paragraph");
  }

  @Test
  void convert_renders_card_variants_without_losing_identity_or_escaping() {
    // Most cases mirror docs/spec card shapes; the final embedCard omits its schema-required url to
    // pin the malformed fallback instead of letting bad producer data throw.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "paragraph",
              "content": [
                { "type": "text", "text": "Inline " },
                { "type": "inlineCard", "attrs": { "url": "https://example.com/ticket/123" } }
              ]
            },
            {
              "type": "blockCard",
              "attrs": { "url": "/wiki/spaces/DEV/pages/123" }
            },
            {
              "type": "blockCard",
              "attrs": {
                "data": {
                  "@type": "Object",
                  "name": "Dashboard *42*",
                  "url": "https://example.com/dashboards/42"
                }
              }
            },
            {
              "type": "blockCard",
              "attrs": {
                "datasource": {
                  "id": "source-1",
                  "parameters": {},
                  "views": [{ "type": "table" }]
                }
              }
            },
            {
              "type": "embedCard",
              "attrs": { "layout": "center" }
            }
          ]
        }
        """;

    var markdown = processor.toMarkdown(adf);

    assertThat(markdown)
        .isEqualToNormalizingNewlines(
            """
            Inline <https://example.com/ticket/123>

            [/wiki/spaces/DEV/pages/123](/wiki/spaces/DEV/pages/123)

            [Dashboard \\*42\\*](https://example.com/dashboards/42)

            \\[Card: source-1\\]

            \\[Embed card\\]
            """.strip());
  }

  @Test
  void convert_escapes_text_after_hard_break_as_a_new_line_start() {
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "paragraph",
              "content": [
                { "type": "text", "text": "alpha" },
                { "type": "hardBreak" },
                { "type": "text", "text": "# beta" }
              ]
            }
          ]
        }
        """;

    var markdown = processor.toMarkdown(adf);

    assertThat(markdown).isEqualToNormalizingNewlines("alpha  \n\\# beta");
  }

  @Test
  void collapse_hard_breaks_renders_a_soft_break_without_trailing_spaces() {
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "paragraph",
              "content": [
                { "type": "text", "text": "alpha" },
                { "type": "hardBreak" },
                { "type": "text", "text": "# beta" }
              ]
            }
          ]
        }
        """;

    var collapsing =
        AdfToMarkdown.with(MarkdownOptions.defaults().withCollapseHardBreaks(true));
    var markdown = collapsing.toMarkdown(adf);

    assertThat(markdown).isEqualToNormalizingNewlines("alpha\n\\# beta");
    assertThat(markdown).doesNotContain("  \n");
  }
}
