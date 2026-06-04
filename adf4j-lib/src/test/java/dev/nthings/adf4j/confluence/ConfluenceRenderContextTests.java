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
