package dev.nthings.adf4j.confluence;

import java.util.List;
import java.util.Map;

import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.metadata.AttachmentReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfluenceRenderContextTests {

  @Test
  void with_attachment_references_normalizes_titles_skips_invalid_entries_and_keeps_first_match() {
    var context = ConfluenceRenderContext.empty()
        .withAttachmentReferences(
            List.of(
                new AttachmentReference(
                    "file-1", "Guide.PDF", "application/pdf"),
                new AttachmentReference(
                    "file-2", " guide.pdf ", "application/octet-stream"),
                new AttachmentReference(
                    "file-3", "diagram.png", "image/png"),
                new AttachmentReference("", "ignored.txt", "text/plain"),
                new AttachmentReference("file-4", "   ", "text/plain")));

    var byTitle = context.attachmentReferencesByTitle();
    assertThat(byTitle).containsOnlyKeys("guide.pdf", "diagram.png");
    assertThat(byTitle.get("guide.pdf").fileId()).isEqualTo("file-1");
    assertThat(byTitle.get("guide.pdf").mediaType()).isEqualTo("application/pdf");
    assertThat(byTitle.get("diagram.png").fileId()).isEqualTo("file-3");
  }

  @Test
  void an_empty_context_has_no_supplied_inventory() {
    assertThat(ConfluenceRenderContext.empty().attachmentsSupplied()).isFalse();
  }

  @Test
  void with_attachment_references_marks_the_inventory_supplied_even_when_empty() {
    var context = ConfluenceRenderContext.empty().withAttachmentReferences(List.of());

    assertThat(context.attachmentsSupplied()).isTrue();
    assertThat(context.attachmentReferencesByTitle()).isEmpty();
  }

  @Test
  void with_a_null_iterable_the_supplied_flag_is_unchanged() {
    assertThat(ConfluenceRenderContext.empty().withAttachmentReferences(null)
        .attachmentsSupplied()).isFalse();
    assertThat(ConfluenceRenderContext.empty().withAttachmentReferences(List.of())
        .withAttachmentReferences(null).attachmentsSupplied()).isTrue();
  }

  @Test
  void confluence_metadata_reads_private_attrs_from_product_neutral_attributes() {
    var metadata = ConfluenceMetadata.from(new Attributes(
        Map.<String, Object>of(
            "__confluenceMetadata",
            Map.of(
                "linkType", "page",
                "pageId", "12345",
                "contentId", "67890",
                "id", "content-1"))));

    assertThat(metadata)
        .isEqualTo(new ConfluenceMetadata("page", "12345", "67890", "content-1"));
  }
}
