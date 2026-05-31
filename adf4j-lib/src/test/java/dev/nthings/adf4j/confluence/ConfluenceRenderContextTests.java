package dev.nthings.adf4j.confluence;

import java.util.List;

import dev.nthings.adf4j.AttachmentReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfluenceRenderContextTests {

  @Test
  void with_attachment_references_normalizes_titles_skips_invalid_entries_and_keeps_first_match() {
    var context = ConfluenceRenderContext.forPage("Fixture")
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

    assertThat(context.attachmentReferencesByTitle()).hasSize(2);
    assertThat(context.attachmentReference(" guide.pdf ").fileId()).isEqualTo("file-1");
    assertThat(context.attachmentReference("GUIDE.PDF").mediaType()).isEqualTo("application/pdf");
    assertThat(context.attachmentReference("diagram.png").fileId()).isEqualTo("file-3");
    assertThat(context.attachmentReference("missing.pdf")).isNull();
  }
}
