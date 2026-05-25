package dev.nthings.adf4j;

import java.util.List;

import dev.nthings.adf4j.model.UnknownNodePolicy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdfStorageDocumentServiceTests {

  private final AdfTestSupport testSupport = AdfTestSupport.create();
  private final AdfStorageDocumentService storageDocumentService = testSupport.storageDocumentService();

  @Test
  void render_storage_document_returns_empty_metadata_for_blank_input() {
    var result = storageDocumentService.renderStorageDocument("   ", RenderOptions.defaults("Ignored"));

    assertThat(result.body()).isEmpty();
    assertThat(result.outputFormat()).isEqualTo(OutputFormat.STORAGE_MARKDOWN);
    assertThat(result.metadata()).isEqualTo(ContentMetadata.empty());
  }

  @Test
  void render_storage_document_falls_back_to_normalized_raw_markdown_for_invalid_adf_roots() {
    var rawPayload = "{\"type\":\"paragraph\",\"version\":1,\"content\":[]}";

    var result = storageDocumentService.renderStorageDocument(rawPayload, RenderOptions.defaults("Ignored"));

    assertThat(result.body())
        .isEqualTo(storageDocumentService.normalizeStorageMarkdown(rawPayload));
    assertThat(result.metadata()).isEqualTo(ContentMetadata.empty());
  }

  @Test
  void render_storage_document_matches_the_reporte_regression_case() throws Exception {
    var result = storageDocumentService.renderStorageDocument(
        testSupport.caseInput("reporte"), RenderOptions.defaults("Report Fixture"));

    assertThat(result.body())
        .isEqualToNormalizingNewlines(testSupport.caseOutput("reporte", ".storage.md"))
        .doesNotContain("<table")
        .doesNotContain("<img")
        .doesNotContain("<span style=")
        .doesNotContain("<p style=");
  }

  @Test
  void render_storage_document_resolves_viewpdf_macros_with_attachment_context() throws Exception {
    var rawPayload = testSupport.caseInput("viewpdf-macros");
    var options = RenderOptions.defaults("Guide Fixture")
        .withAttachmentReferences(
            List.of(new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf")));

    var result = storageDocumentService.renderStorageDocument(rawPayload, options);

    assertThat(result.body()).isEqualTo("[PDF: guide.pdf](attachment:file-pdf-1)");
    assertThat(result.metadata().attachmentRefs())
        .singleElement()
        .isEqualTo(new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf"));
  }

  @Test
  void render_storage_document_applies_the_unknown_node_policy() throws Exception {
    var rawPayload = testSupport.caseInput("unknown-node-policy");

    assertThat(
        storageDocumentService.renderStorageMarkdown(
            rawPayload,
            RenderOptions.defaults("Unknown")
                .withUnknownNodePolicy(UnknownNodePolicy.PLACEHOLDER)))
        .contains("[Unsupported: mysteryBlock]");
    assertThat(
        storageDocumentService.renderStorageMarkdown(
            rawPayload,
            RenderOptions.defaults("Unknown").withUnknownNodePolicy(UnknownNodePolicy.SKIP)))
        .isEmpty();
    assertThatThrownBy(
        () -> storageDocumentService.renderStorageMarkdown(
            rawPayload,
            RenderOptions.defaults("Unknown").withUnknownNodePolicy(UnknownNodePolicy.FAIL)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("mysteryBlock");
  }

  @Test
  void render_storage_document_keeps_children_macros_as_placeholders_for_db_derived_cases()
      throws Exception {
    var markdown = storageDocumentService.renderStorageMarkdown(
        testSupport.caseInput("especificacoes-reporte-children"),
        RenderOptions.defaults("Reporting Specifications"));

    assertThat(markdown).contains("{{children}}").contains("Reporting Specifications");
  }

  @Test
  void render_storage_document_resolves_db_derived_viewpdf_cases_with_attachment_context()
      throws Exception {
    var options = RenderOptions.defaults("Participant Guide")
        .withAttachmentReferences(
            List.of(
                new AttachmentReference(
                    "file-pdf-123",
                    "Open_Finance_cadastro_diretorio_passo_a_passo.pdf",
                    "application/pdf")));

    var result = storageDocumentService.renderStorageDocument(
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
}
