package dev.nthings.adf4j;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenderOptionsTests {

  @Test
  void with_attachment_references_normalizes_titles_skips_invalid_entries_and_keeps_first_match() {
    var options = RenderOptions.defaults("Fixture")
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

    assertThat(options.attachmentReferencesByTitle()).hasSize(2);
    assertThat(options.attachmentReference(" guide.pdf ").fileId()).isEqualTo("file-1");
    assertThat(options.attachmentReference("GUIDE.PDF").mediaType()).isEqualTo("application/pdf");
    assertThat(options.attachmentReference("diagram.png").fileId()).isEqualTo("file-3");
    assertThat(options.attachmentReference("missing.pdf")).isNull();
  }

  @Test
  void with_child_pages_sanitizes_invalid_entries_recursively_and_returns_immutable_children() {
    var options = RenderOptions.defaults("Fixture")
        .withChildPages(
            List.of(
                new RenderOptions.ChildPage(
                    "page-1",
                    "Page One",
                    List.of(
                        new RenderOptions.ChildPage("child-1", "Child One"),
                        new RenderOptions.ChildPage("", "Ignored child"),
                        new RenderOptions.ChildPage("child-2", "   "))),
                new RenderOptions.ChildPage(" ", "Ignored page"),
                new RenderOptions.ChildPage("page-2", "Page Two")));

    assertThat(options.childPages()).hasSize(2);
    assertThat(options.childPages().get(0).nodeId()).isEqualTo("page-1");
    assertThat(options.childPages().get(0).children())
        .containsExactly(new RenderOptions.ChildPage("child-1", "Child One"));
    assertThat(options.childPages().get(1))
        .isEqualTo(new RenderOptions.ChildPage("page-2", "Page Two"));
    assertThatThrownBy(
        () -> options.childPages().add(new RenderOptions.ChildPage("page-3", "Page Three")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
