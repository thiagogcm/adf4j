package dev.nthings.adf4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.metadata.AttachmentReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AttachmentInventoryTests {

  @Test
  void a_null_list_means_the_inventory_is_unknown() {
    var inventory = AttachmentInventory.from(null);

    assertThat(inventory.isKnown()).isFalse();
    assertThat(inventory.entries()).isEmpty();
  }

  @Test
  void a_list_is_authoritative_even_when_empty() {
    var inventory = AttachmentInventory.from(List.of());

    assertThat(inventory.isKnown()).isTrue();
    assertThat(inventory.entries()).isEmpty();
  }

  @Test
  void titles_match_case_insensitively_and_trimmed_and_the_first_entry_wins() {
    var first = new AttachmentReference("file-1", "Guide.PDF", "application/pdf");
    var second = new AttachmentReference("file-2", " guide.pdf ", "application/octet-stream");

    var inventory = AttachmentInventory.from(List.of(first, second));

    assertThat(inventory.byTitle(" GUIDE.pdf")).isSameAs(first);
  }

  @Test
  void attachments_sharing_a_title_both_stay_reachable_by_file_id() {
    var first = new AttachmentReference("file-1", "report.pdf", null, "https://x/1");
    var second = new AttachmentReference("file-2", "report.pdf", null, "https://x/2");

    var inventory = AttachmentInventory.from(List.of(first, second));

    assertThat(inventory.entries()).containsExactly(first, second);
    assertThat(inventory.byFileId("file-1")).isSameAs(first);
    assertThat(inventory.byFileId("file-2")).isSameAs(second);
  }

  @Test
  void a_title_less_entry_is_kept_and_resolves_by_file_id_only() {
    var untitled = new AttachmentReference("file-1", null, null, "https://x/1");
    var blank = new AttachmentReference("file-2", "   ", null);

    var inventory = AttachmentInventory.from(List.of(untitled, blank));

    assertThat(inventory.entries()).containsExactly(untitled, blank);
    assertThat(inventory.byFileId("file-1")).isSameAs(untitled);
    assertThat(inventory.byFileId("file-2")).isSameAs(blank);
    assertThat(inventory.byTitle("   ")).isNull();
    assertThat(inventory.byTitle(null)).isNull();
  }

  @Test
  void file_ids_are_the_identity_matched_case_insensitively_and_trimmed() {
    var first = new AttachmentReference("File-1", "a.pdf", null);
    var duplicate = new AttachmentReference(" file-1 ", "b.pdf", null);

    var inventory = AttachmentInventory.from(List.of(first, duplicate));

    assertThat(inventory.entries()).containsExactly(first);
    assertThat(inventory.byFileId(" FILE-1 ")).isSameAs(first);
    assertThat(inventory.byTitle("b.pdf")).isNull();
    assertThat(inventory.byFileId(null)).isNull();
    assertThat(inventory.byFileId("  ")).isNull();
  }

  @Test
  void an_entry_with_a_blank_file_id_is_dropped() {
    var inventory =
        AttachmentInventory.from(
            List.of(new AttachmentReference(" ", "ignored.txt", "text/plain")));

    assertThat(inventory.entries()).isEmpty();
    assertThat(inventory.byTitle("ignored.txt")).isNull();
  }

  @Test
  void entries_keep_document_order() {
    var attachments =
        List.of(
            new AttachmentReference("c", "c", null),
            new AttachmentReference("a", null, null),
            new AttachmentReference("b", "b", null));

    assertThat(AttachmentInventory.from(attachments).entries())
        .containsExactlyElementsOf(attachments);
  }

  @Test
  void confluence_metadata_reads_private_attrs_from_product_neutral_attributes() {
    var metadata =
        ConfluenceMetadata.from(
            new Attributes(
                Map.<String, Object>of(
                    "__confluenceMetadata",
                    Map.of(
                        "linkType", "page", "pageId", "12345", "contentId", "67890", "id", "c1"))));

    assertThat(metadata).isEqualTo(new ConfluenceMetadata("12345", "67890", "c1"));
  }

  @Test
  void a_link_type_alone_does_not_make_a_link_a_page() {
    var metadata =
        ConfluenceMetadata.from(
            new Attributes(
                Map.<String, Object>of("__confluenceMetadata", Map.of("linkType", "page"))));

    assertThat(ConfluenceSupport.pageNodeId("https://example.com/not-a-page", metadata)).isNull();
  }

  @Test
  void the_url_page_id_wins_over_the_metadata_id() {
    var metadata = new ConfluenceMetadata("meta", null, null);

    assertThat(ConfluenceSupport.pageNodeId("/wiki/spaces/S/pages/42/Title", metadata))
        .isEqualTo("42");
    assertThat(ConfluenceSupport.pageNodeId("https://example.com/other", metadata))
        .isEqualTo("meta");
    assertThat(
            ConfluenceSupport.pageNodeId("https://example.com/other", ConfluenceMetadata.empty()))
        .isNull();
  }
}
