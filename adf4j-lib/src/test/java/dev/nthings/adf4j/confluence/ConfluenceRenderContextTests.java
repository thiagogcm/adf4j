package dev.nthings.adf4j.confluence;

import java.util.List;

import dev.nthings.adf4j.AttachmentReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

  @Test
  void with_child_pages_sanitizes_invalid_entries_recursively_and_returns_immutable_children() {
    var context = ConfluenceRenderContext.forPage("Fixture")
        .withChildPages(
            List.of(
                new ConfluenceRenderContext.ChildPage(
                    "page-1",
                    "Page One",
                    List.of(
                        new ConfluenceRenderContext.ChildPage("child-1", "Child One"),
                        new ConfluenceRenderContext.ChildPage("", "Ignored child"),
                        new ConfluenceRenderContext.ChildPage("child-2", "   "))),
                new ConfluenceRenderContext.ChildPage(" ", "Ignored page"),
                new ConfluenceRenderContext.ChildPage("page-2", "Page Two")));

    assertThat(context.childPages()).hasSize(2);
    assertThat(context.childPages().get(0).nodeId()).isEqualTo("page-1");
    assertThat(context.childPages().get(0).children())
        .containsExactly(new ConfluenceRenderContext.ChildPage("child-1", "Child One"));
    assertThat(context.childPages().get(1))
        .isEqualTo(new ConfluenceRenderContext.ChildPage("page-2", "Page Two"));
    assertThatThrownBy(
        () -> context.childPages().add(new ConfluenceRenderContext.ChildPage("page-3", "Page Three")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
