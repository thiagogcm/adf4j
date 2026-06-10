package dev.nthings.adf4j;

import java.util.List;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ExternalReference;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.metadata.MentionReference;
import dev.nthings.adf4j.metadata.PageReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.PageTreeMacro;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdfToMarkdownMetadataTests {

  private final AdfTestSupport testSupport = AdfTestSupport.create();

  @Test
  void extract_collects_external_refs_and_durable_attachment_refs_from_db_derived_viewpdf_case()
      throws Exception {
    var options = MarkdownOptions.defaults()
        .withContext(
            ConfluenceRenderContext.empty()
                .withAttachmentReferences(
                    List.of(
                        new AttachmentReference(
                            "file-pdf-123",
                            "Open_Finance_cadastro_diretorio_passo_a_passo.pdf",
                            "application/pdf"))));

    var metadata = AdfToMarkdown.with(options)
        .convert(testSupport.caseDocument("lista-participantes-viewpdf"))
        .metadata();

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
    var options = MarkdownOptions.defaults()
        .withContext(
            ConfluenceRenderContext.empty()
                .withAttachmentReferences(
                    List.of(new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf"))));

    var metadata = AdfToMarkdown.with(options).convert(adf).metadata();

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
  void referenced_file_ids_expose_only_the_attachments_the_body_embeds() throws Exception {
    var metadata = testSupport.processor()
        .convert(
            testSupport.parseDocument(
                """
                {
                  "type": "doc",
                  "version": 1,
                  "content": [
                    {
                      "type": "mediaSingle",
                      "attrs": { "layout": "center" },
                      "content": [
                        { "type": "media", "attrs": { "type": "file", "id": "asset-1", "alt": "a" } }
                      ]
                    },
                    {
                      "type": "mediaGroup",
                      "content": [
                        { "type": "media", "attrs": { "type": "file", "id": "asset-2", "fileName": "b.png" } },
                        { "type": "media", "attrs": { "type": "external", "url": "https://cdn.example.com/c.png" } }
                      ]
                    }
                  ]
                }
                """))
        .metadata();

    // The external media contributes no file id; only the two embedded attachments are referenced.
    assertThat(metadata.referencedFileIds()).containsExactly("asset-1", "asset-2");
  }

  @Test
  void extract_lists_each_page_tree_macro_occurrence_with_its_normalized_root() throws Exception {
    var metadata = testSupport.processor()
        .convert(
            testSupport.parseDocument(
                """
                {
                  "type": "doc",
                  "version": 1,
                  "content": [
                    {
                      "type": "extension",
                      "attrs": {
                        "extensionType": "com.atlassian.confluence.macro.core",
                        "extensionKey": "pagetree",
                        "parameters": { "macroParams": { "root": { "value": "Docs Home" } } }
                      }
                    },
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "inlineExtension",
                          "attrs": {
                            "extensionType": "com.atlassian.confluence.macro.core",
                            "extensionKey": "children",
                            "parameters": { "macroParams": { "page": { "value": "@self" } } }
                          }
                        }
                      ]
                    }
                  ]
                }
                """))
        .metadata();

    // The keyword root normalizes to null, the same way PageTreeRequest.root() reports it.
    assertThat(metadata.pageTreeRefs())
        .containsExactly(
            new PageTreeReference(PageTreeMacro.PAGETREE, "Docs Home"),
            new PageTreeReference(PageTreeMacro.CHILDREN, null));
  }

  @Test
  void extract_collects_mentions_with_first_non_blank_id() throws Exception {
    var metadata = testSupport.processor()
        .convert(
            testSupport.parseDocument(
                """
                {
                  "type": "doc",
                  "version": 1,
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {"type": "text", "text": "Owner: "},
                        {"type": "mention", "attrs": {"id": "user-9", "text": "@Ada"}}
                      ]
                    }
                  ]
                }
                """))
        .metadata();

    assertThat(metadata.mentionRefs())
        .containsExactly(new MentionReference("user-9", "@Ada"));
  }

  @Test
  void extract_populates_the_heading_outline_with_levels_text_and_anchors() throws Exception {
    var metadata = testSupport.processor()
        .convert(testSupport.caseDocument("anchor-macros"))
        .metadata();

    assertThat(metadata.outline())
        .containsExactly(new HeadingReference(2, "Section A", "custom-section"));
  }

  @Test
  void extract_generates_stable_anchor_suffixes_for_repeated_headings() throws Exception {
    var metadata = testSupport.processor()
        .convert(
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
                """))
        .metadata();

    assertThat(metadata.outline())
        .containsExactly(
            new HeadingReference(2, "Section", "section"),
            new HeadingReference(3, "Section", "section-1"),
            new HeadingReference(3, "Section", "section-2"));
  }
}
