package dev.nthings.adf4j;

import java.util.List;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdfConverterMetadataTests {

  private final AdfTestSupport testSupport = AdfTestSupport.create();

  @Test
  void extract_collects_external_refs_and_durable_attachment_refs_from_db_derived_viewpdf_case()
      throws Exception {
    var options = RenderOptions.defaults()
        .withContext(
            ConfluenceRenderContext.forPage("Participant Guide")
                .withAttachmentReferences(
                    List.of(
                        new AttachmentReference(
                            "file-pdf-123",
                            "Open_Finance_cadastro_diretorio_passo_a_passo.pdf",
                            "application/pdf"))));

    var metadata = testSupport
        .processor()
        .metadata(testSupport.caseDocument("lista-participantes-viewpdf"), options);

    assertThat(metadata.pageRefs()).isEmpty();
    assertThat(metadata.externalRefs())
        .extracting(ExternalReference::url)
        .containsExactly(
            "https://data.directory.openbankingbrasil.org.br/participants",
            "https://github.com/OpenBanking-Brasil/specs-directory/blob/main/swagger_participants.yaml",
            "https://openfinancebrasil.atlassian.net/wiki/download/attachments/7996604/Open_Finance_cadastro_diretorio_passo_a_passo.pdf?api=v2");
    assertThat(metadata.attachmentRefs())
        .singleElement()
        .isEqualTo(
            new AttachmentReference(
                "file-pdf-123",
                "Open_Finance_cadastro_diretorio_passo_a_passo.pdf",
                "application/pdf"));
  }

  @Test
  void extract_deduplicates_repeated_refs_and_preserves_first_seen_encounter_order()
      throws Exception {
    var adf = testSupport.caseDocument("deduplicate-references");
    var options = RenderOptions.defaults()
        .withContext(
            ConfluenceRenderContext.forPage("Metadata Fixture")
                .withAttachmentReferences(
                    List.of(new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf"))));

    var metadata = testSupport.processor().metadata(adf, options);

    assertThat(metadata.pageRefs())
        .extracting(PageReference::pageNodeId)
        .containsExactly("12345", "67890");
    assertThat(metadata.externalRefs())
        .extracting(ExternalReference::url)
        .containsExactly(
            "https://external.example.com/docs", "https://cdn.example.com/diagram.png");
    assertThat(metadata.attachmentRefs())
        .containsExactly(
            new AttachmentReference("asset-22", "diagram.png", "image/png"),
            new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf"));
  }

  @Test
  void extract_populates_the_heading_outline_with_levels_text_and_anchors() throws Exception {
    var metadata = testSupport
        .processor()
        .metadata(testSupport.caseDocument("anchor-macros"), RenderOptions.defaults());

    assertThat(metadata.outline())
        .containsExactly(new HeadingReference(2, "Section A", "custom-section"));
  }

  @Test
  void extract_generates_stable_anchor_suffixes_for_repeated_headings() throws Exception {
    var metadata = testSupport
        .processor()
        .metadata(
            testSupport.parseDocument(
                """
                {
                  "type": "doc",
                  "version": 1,
                  "content": [
                    {"type": "heading", "attrs": {"level": 2}, "content": [{"type": "text", "text": "Section"}]},
                    {"type": "heading", "attrs": {"level": 3}, "content": [{"type": "text", "text": "Section"}]},
                    {"type": "heading", "attrs": {"level": 3}, "content": [{"type": "text", "text": "Section"}]}
                  ]
                }
                """),
            RenderOptions.defaults());

    assertThat(metadata.outline())
        .containsExactly(
            new HeadingReference(2, "Section", "section"),
            new HeadingReference(3, "Section", "section-1"),
            new HeadingReference(3, "Section", "section-2"));
  }
}
