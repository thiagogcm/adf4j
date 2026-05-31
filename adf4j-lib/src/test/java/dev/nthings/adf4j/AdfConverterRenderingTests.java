package dev.nthings.adf4j;

import java.util.List;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.UnknownNodePolicy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdfConverterRenderingTests {

  private final AdfTestSupport testSupport = AdfTestSupport.create();
  private final AdfConverter processor = testSupport.processor();

  @Test
  void render_returns_empty_metadata_for_blank_input() {
    var result = processor.render("   ", RenderOptions.defaults());

    assertThat(result.body()).isEmpty();
    assertThat(result.metadata()).isEqualTo(ContentMetadata.empty());
  }

  @Test
  void render_returns_empty_result_with_diagnostics_for_invalid_adf_roots() {
    var rawPayload = "{\"type\":\"paragraph\",\"version\":1,\"content\":[]}";

    var result = processor.render(rawPayload, RenderOptions.defaults());

    assertThat(result.body()).isEmpty();
    assertThat(result.metadata()).isEqualTo(ContentMetadata.empty());
    assertThat(result.diagnostics()).isNotEmpty();
  }

  @Test
  void render_matches_the_reporte_regression_case() throws Exception {
    var result = processor.render(
        testSupport.caseInput("reporte"), optionsForPage("Report Fixture"));

    assertThat(result.body())
        .isEqualToNormalizingNewlines(testSupport.caseOutput("reporte", ".md"))
        .doesNotContain("<table")
        .doesNotContain("<img")
        .doesNotContain("<span style=")
        .doesNotContain("<p style=");
  }

  @Test
  void render_resolves_viewpdf_macros_with_attachment_context() throws Exception {
    var rawPayload = testSupport.caseInput("viewpdf-macros");
    var options = RenderOptions.defaults()
        .withContext(
            ConfluenceRenderContext.forPage("Guide Fixture")
                .withAttachmentReferences(
                    List.of(new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf"))));

    var result = processor.render(rawPayload, options);

    assertThat(result.body()).isEqualTo("[PDF: guide.pdf](attachment:file-pdf-1)");
    assertThat(result.metadata().attachmentRefs())
        .singleElement()
        .isEqualTo(new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf"));
  }

  @Test
  void render_applies_the_unknown_node_policy() throws Exception {
    var rawPayload = testSupport.caseInput("unknown-node-policy");

    assertThat(
        processor.toMarkdown(
            rawPayload,
            RenderOptions.defaults()
                .withUnknownNodePolicy(UnknownNodePolicy.PLACEHOLDER)))
        .contains("[Unsupported: mysteryBlock]");
    assertThat(
        processor.toMarkdown(
            rawPayload,
            RenderOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.SKIP)))
        .isEmpty();
    assertThatThrownBy(
        () -> processor.toMarkdown(
            rawPayload,
            RenderOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.FAIL)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("mysteryBlock");
  }

  @Test
  void render_supports_generic_options_without_context() throws Exception {
    var markdown = processor.toMarkdown(
        testSupport.caseInput("unknown-node-policy"),
        RenderOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.SKIP));

    assertThat(markdown).isEmpty();
  }

  @Test
  void render_keeps_children_macros_as_placeholders_for_db_derived_cases() throws Exception {
    var markdown = processor.toMarkdown(
        testSupport.caseInput("especificacoes-reporte-children"),
        optionsForPage("Reporting Specifications"));

    assertThat(markdown).contains("{{children}}").contains("Reporting Specifications");
  }

  @Test
  void render_resolves_db_derived_viewpdf_cases_with_attachment_context() throws Exception {
    var options = RenderOptions.defaults()
        .withContext(
            ConfluenceRenderContext.forPage("Participant Guide")
                .withAttachmentReferences(
                    List.of(
                        new AttachmentReference(
                            "file-pdf-123",
                            "Open_Finance_cadastro_diretorio_passo_a_passo.pdf",
                            "application/pdf"))));

    var result = processor.render(
        testSupport.caseInput("lista-participantes-viewpdf"), options);

    assertThat(result.body())
        .contains("[PDF: Open_Finance_cadastro_diretorio_passo_a_passo.pdf](attachment:file-pdf-123)");
    assertThat(result.metadata().attachmentRefs())
        .singleElement()
        .satisfies(ref -> {
          assertThat(ref.fileId()).isEqualTo("file-pdf-123");
          assertThat(ref.title()).isEqualTo("Open_Finance_cadastro_diretorio_passo_a_passo.pdf");
        });
  }

  private static RenderOptions optionsForPage(String pageTitle) {
    return RenderOptions.defaults().withContext(ConfluenceRenderContext.forPage(pageTitle));
  }
}
