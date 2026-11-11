package dev.nthings.adf4j.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.testing.TestResources;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentMetadataTests {

  private static final AdfToMarkdown CONVERTER = AdfToMarkdown.create();

  @Test
  void extract_collects_external_refs_and_durable_attachment_refs_from_db_derived_viewpdf_case()
      throws Exception {
    var options =
        ConversionOptions.defaults()
            .withAttachments(
                List.of(
                    new AttachmentReference(
                        "file-pdf-123",
                        "Open_Finance_cadastro_diretorio_passo_a_passo.pdf",
                        "application/pdf")));

    var metadata =
        AdfToMarkdown.with(options)
            .convert(TestResources.specDocument("lista-participantes-viewpdf"))
            .metadata();

    assertThat(metadata.pageIds()).isEmpty();
    assertThat(metadata.externalUrls())
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
    var adf = TestResources.specDocument("deduplicate-references");
    var options =
        ConversionOptions.defaults()
            .withAttachments(
                List.of(new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf")));

    var metadata = AdfToMarkdown.with(options).convert(adf).metadata();

    assertThat(metadata.pageIds()).containsExactly("12345", "67890");
    assertThat(metadata.externalUrls())
        .containsExactly(
            "https://external.example.com/docs", "https://cdn.example.com/diagram.png");
    assertThat(metadata.attachmentRefs())
        .containsExactly(
            new AttachmentReference("asset-22", "diagram.png", "image/png"),
            new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf"));
  }

  @Test
  void a_media_node_in_the_inventory_is_enriched_with_what_it_lacks() {
    // A Confluence file node: the only hint at its kind is the coarse `type`.
    var options =
        ConversionOptions.defaults()
            .withAttachments(
                List.of(
                    new AttachmentReference("f1", "plan.pdf", "application/pdf", "https://x/1")));

    var metadata = AdfToMarkdown.with(options).convert(media("f1", "")).metadata();

    assertThat(metadata.attachmentRefs())
        .containsExactly(
            new AttachmentReference("f1", "plan.pdf", "application/pdf", "https://x/1"));
  }

  @Test
  void a_media_node_keeps_what_it_has_over_the_inventory() {
    var options =
        ConversionOptions.defaults()
            .withAttachments(
                List.of(
                    new AttachmentReference("f1", "plan.pdf", "application/pdf", "https://x/1")));
    var ownFields = ", \"__fileName\": \"own.png\", \"__fileMimeType\": \"image/png\"";

    var metadata = AdfToMarkdown.with(options).convert(media("f1", ownFields)).metadata();

    assertThat(metadata.attachmentRefs())
        .containsExactly(new AttachmentReference("f1", "own.png", "image/png", "https://x/1"));
  }

  @Test
  void a_repeated_media_node_lacking_fields_does_not_replace_the_ones_another_node_set() {
    var options =
        ConversionOptions.defaults()
            .withAttachments(
                List.of(
                    new AttachmentReference("f1", "plan.pdf", "application/pdf", "https://x/1")));
    var repeated =
        """
        {"type": "doc", "version": 1, "content": [
          {"type": "mediaGroup", "content": [
            {"type": "media", "attrs": {"type": "file", "id": "f1", "__fileName": "own.pdf"}},
            {"type": "media", "attrs": {"type": "file", "id": "f1"}}
          ]}
        ]}
        """;

    var metadata = AdfToMarkdown.with(options).convert(repeated).metadata();

    assertThat(metadata.attachmentRefs())
        .containsExactly(
            new AttachmentReference("f1", "own.pdf", "application/pdf", "https://x/1"));
  }

  @Test
  void inventory_lookup_ignores_case_and_surrounding_whitespace_of_the_file_id() {
    var options =
        ConversionOptions.defaults()
            .withAttachments(
                List.of(new AttachmentReference("F1", "plan.pdf", null, "https://x/1")));

    var metadata = AdfToMarkdown.with(options).convert(media("f1", "")).metadata();

    assertThat(metadata.attachmentRefs())
        .containsExactly(
            new AttachmentReference("F1", "plan.pdf", "application/pdf", "https://x/1"));
  }

  @Test
  void a_media_node_outside_the_inventory_is_left_as_it_is() {
    var options =
        ConversionOptions.defaults()
            .withAttachments(
                List.of(new AttachmentReference("f2", "plan.pdf", null, "https://x/2")));

    var metadata = AdfToMarkdown.with(options).convert(media("f1", "")).metadata();

    assertThat(metadata.attachmentRefs())
        .containsExactly(new AttachmentReference("f1", null, null));
  }

  @Test
  void attachment_macros_carry_the_inventory_download_url() {
    var inventory =
        List.of(new AttachmentReference("f1", "guide.pdf", "application/pdf", "https://x/1"));

    var metadata =
        AdfToMarkdown.with(ConversionOptions.defaults().withAttachments(inventory))
            .convert(viewPdf("guide.pdf"))
            .metadata();

    assertThat(metadata.attachmentRefs()).isEqualTo(inventory);
  }

  private static String media(String id, String extraAttrs) {
    return """
        {"type": "doc", "version": 1, "content": [
          {"type": "mediaSingle", "content": [
            {"type": "media", "attrs": {"type": "file", "id": "%s"%s}}
          ]}
        ]}
        """
        .formatted(id, extraAttrs);
  }

  private static String viewPdf(String name) {
    return """
        {"type": "doc", "version": 1, "content": [
          {"type": "extension", "attrs": {
            "extensionType": "com.atlassian.confluence.macro.core",
            "extensionKey": "viewpdf",
            "parameters": {"macroParams": {"name": {"value": "%s"}}}
          }}
        ]}
        """
        .formatted(name);
  }

  @Test
  void referenced_file_ids_expose_only_the_attachments_the_body_embeds() throws Exception {
    var metadata =
        CONVERTER
            .convert(
                AdfJson.parse(
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
    var metadata =
        CONVERTER
            .convert(
                AdfJson.parse(
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

    // The keyword root normalizes to null, the same way PageTreeReference.root() reports it.
    assertThat(metadata.pageTreeRefs())
        .containsExactly(
            new PageTreeReference(PageTreeMacro.PAGETREE, "Docs Home", Map.of("root", "Docs Home")),
            new PageTreeReference(PageTreeMacro.CHILDREN, null, Map.of("page", "@self")));
  }

  @Test
  void extract_collects_mentions_with_first_non_blank_id() throws Exception {
    var metadata =
        CONVERTER
            .convert(
                AdfJson.parse(
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

    assertThat(metadata.mentionRefs()).containsExactly(new MentionReference("user-9", "@Ada"));
  }

  @Test
  void extract_populates_the_heading_outline_with_levels_text_and_anchors() throws Exception {
    var metadata = CONVERTER.convert(TestResources.specDocument("anchor-macros")).metadata();

    assertThat(metadata.outline())
        .containsExactly(new HeadingReference(2, "Section A", "custom-section"));
  }

  @Test
  void extract_generates_stable_anchor_suffixes_for_repeated_headings() throws Exception {
    var metadata =
        CONVERTER
            .convert(
                AdfJson.parse(
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
