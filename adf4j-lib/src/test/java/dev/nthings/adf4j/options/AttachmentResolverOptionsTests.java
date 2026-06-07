package dev.nthings.adf4j.options;

import java.util.List;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.metadata.AttachmentReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentResolverOptionsTests {

  // A viewpdf macro whose "name" matches an attachment supplied via the render context.
  private static final String VIEWPDF =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "viewpdf",
              "parameters": { "macroParams": { "name": { "value": "guide.pdf" } } }
            }
          }
        ]
      }
      """;

  private static MarkdownOptions withAttachmentContext(AttachmentResolver resolver) {
    return MarkdownOptions.defaults()
        .withContext(
            ConfluenceRenderContext.empty()
                .withAttachmentReferences(
                    List.of(new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf"))))
        .withAttachmentResolver(resolver);
  }

  @Test
  void default_emits_the_synthetic_attachment_placeholder() {
    var options = withAttachmentContext(null);

    var markdown = AdfToMarkdown.with(options).toMarkdown(VIEWPDF).strip();

    assertThat(markdown).isEqualTo("[PDF: guide.pdf](attachment:file-pdf-1)");
  }

  @Test
  void resolver_turns_the_resolved_attachment_into_a_local_path() {
    var options =
        withAttachmentContext(reference -> "files/" + reference.title());

    var markdown = AdfToMarkdown.with(options).toMarkdown(VIEWPDF).strip();

    assertThat(markdown).isEqualTo("[PDF: guide.pdf](files/guide.pdf)");
  }

  @Test
  void resolver_sees_the_resolved_file_id_and_media_type() {
    var options =
        withAttachmentContext(
            reference -> "/blobs/" + reference.fileId() + "?type=" + reference.mediaType());

    var markdown = AdfToMarkdown.with(options).toMarkdown(VIEWPDF).strip();

    assertThat(markdown)
        .isEqualTo("[PDF: guide.pdf](/blobs/file-pdf-1?type=application/pdf)");
  }

  @Test
  void resolver_returning_blank_falls_back_to_the_placeholder() {
    var options = withAttachmentContext(reference -> "  ");

    var markdown = AdfToMarkdown.with(options).toMarkdown(VIEWPDF).strip();

    assertThat(markdown).isEqualTo("[PDF: guide.pdf](attachment:file-pdf-1)");
  }

  @Test
  void hostile_filename_in_the_label_is_inline_escaped() {
    var hostileViewpdf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "extension",
              "attrs": {
                "extensionType": "com.atlassian.confluence.macro.core",
                "extensionKey": "viewpdf",
                "parameters": { "macroParams": { "name": { "value": "Report [final]*.pdf" } } }
              }
            }
          ]
        }
        """;
    var options =
        MarkdownOptions.defaults()
            .withContext(
                ConfluenceRenderContext.empty()
                    .withAttachmentReferences(
                        List.of(
                            new AttachmentReference(
                                "file-pdf-1", "Report [final]*.pdf", "application/pdf"))));

    var markdown = AdfToMarkdown.with(options).toMarkdown(hostileViewpdf).strip();

    assertThat(markdown).isEqualTo("[PDF: Report \\[final\\]\\*.pdf](attachment:file-pdf-1)");
  }
}
