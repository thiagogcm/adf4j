package dev.nthings.adf4j.options;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.metadata.AttachmentReference;
import java.util.List;
import org.junit.jupiter.api.Test;

/// A supplied attachment inventory carrying `downloadUrl`s makes real links the default: no
/// resolver callback is needed for attachment macros or `media`/`mediaInline` file nodes.
class AttachmentDownloadUrlDefaultsTests {

  private static final String DOWNLOAD_URL =
      "https://example.atlassian.net/wiki/rest/api/content/1/child/attachment/att9/download";

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

  private static final String MEDIA_INLINE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              {
                "type": "mediaInline",
                "attrs": {
                  "id": "cd01b020-40c6-4e88-acf8-ddc6dcce835c",
                  "collection": "contentId-1"
                }
              }
            ]
          }
        ]
      }
      """;

  private static final String MEDIA_SINGLE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "mediaSingle",
            "content": [
              {
                "type": "media",
                "attrs": {
                  "type": "file",
                  "id": "cd01b020-40c6-4e88-acf8-ddc6dcce835c",
                  "collection": "contentId-1"
                }
              }
            ]
          }
        ]
      }
      """;

  private static MarkdownOptions withInventory(AttachmentReference... references) {
    return MarkdownOptions.defaults()
        .withConfluenceContext(
            ConfluenceRenderContext.empty().withAttachmentReferences(List.of(references)));
  }

  @Test
  void viewpdf_links_to_the_inventory_download_url_by_default() {
    var options =
        withInventory(
            new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf", DOWNLOAD_URL));

    var markdown = AdfToMarkdown.with(options).toMarkdown(VIEWPDF).strip();

    assertThat(markdown).isEqualTo("[PDF: guide.pdf](" + DOWNLOAD_URL + ")");
  }

  @Test
  void attachment_resolver_still_wins_over_the_download_url() {
    var options =
        withInventory(
                new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf", DOWNLOAD_URL))
            .withAttachmentResolver(reference -> "files/" + reference.title());

    var markdown = AdfToMarkdown.with(options).toMarkdown(VIEWPDF).strip();

    assertThat(markdown).isEqualTo("[PDF: guide.pdf](files/guide.pdf)");
  }

  @Test
  void attachments_macro_lists_the_download_urls() {
    var attachmentsMacro =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "extension",
              "attrs": {
                "extensionType": "com.atlassian.confluence.macro.core",
                "extensionKey": "attachments"
              }
            }
          ]
        }
        """;
    var options =
        withInventory(
            new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf", DOWNLOAD_URL));

    var markdown = AdfToMarkdown.with(options).toMarkdown(attachmentsMacro).strip();

    assertThat(markdown).isEqualTo("- [guide.pdf](" + DOWNLOAD_URL + ")");
  }

  @Test
  void media_inline_file_node_links_to_the_matching_inventory_entry() {
    var options =
        withInventory(
            new AttachmentReference(
                "cd01b020-40c6-4e88-acf8-ddc6dcce835c",
                "Model spreadsheet.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                DOWNLOAD_URL));

    var markdown = AdfToMarkdown.with(options).toMarkdown(MEDIA_INLINE).strip();

    assertThat(markdown).isEqualTo("[Model spreadsheet.xlsx](" + DOWNLOAD_URL + ")");
  }

  @Test
  void media_image_node_embeds_the_matching_inventory_entry() {
    var options =
        withInventory(
            new AttachmentReference(
                "cd01b020-40c6-4e88-acf8-ddc6dcce835c", "diagram.png", "image/png", DOWNLOAD_URL));

    var markdown = AdfToMarkdown.with(options).toMarkdown(MEDIA_SINGLE).strip();

    assertThat(markdown).isEqualTo("![diagram.png](" + DOWNLOAD_URL + ")");
  }

  @Test
  void media_resolver_still_wins_over_the_inventory_entry() {
    var options =
        withInventory(
                new AttachmentReference(
                    "cd01b020-40c6-4e88-acf8-ddc6dcce835c",
                    "diagram.png",
                    "image/png",
                    DOWNLOAD_URL))
            .withMediaResolver(attrs -> "local/" + attrs.id() + ".png");

    var markdown = AdfToMarkdown.with(options).toMarkdown(MEDIA_SINGLE).strip();

    assertThat(markdown)
        .isEqualTo("![diagram.png](local/cd01b020-40c6-4e88-acf8-ddc6dcce835c.png)");
  }

  @Test
  void media_without_a_matching_entry_keeps_the_synthetic_placeholder() {
    var options =
        withInventory(
            new AttachmentReference("some-other-file", "other.png", "image/png", DOWNLOAD_URL));

    var markdown = AdfToMarkdown.with(options).toMarkdown(MEDIA_INLINE).strip();

    assertThat(markdown)
        .isEqualTo("[file](media:contentId-1/cd01b020-40c6-4e88-acf8-ddc6dcce835c)");
  }

  @Test
  void entry_without_a_download_url_keeps_the_attachment_placeholder() {
    var options =
        withInventory(new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf"));

    var markdown = AdfToMarkdown.with(options).toMarkdown(VIEWPDF).strip();

    assertThat(markdown).isEqualTo("[PDF: guide.pdf](attachment:file-pdf-1)");
  }

  @Test
  void file_id_match_ignores_case_and_surrounding_whitespace() {
    var options =
        withInventory(
            new AttachmentReference(
                " CD01B020-40C6-4E88-ACF8-DDC6DCCE835C ",
                "diagram.png",
                "image/png",
                DOWNLOAD_URL));

    var markdown = AdfToMarkdown.with(options).toMarkdown(MEDIA_SINGLE).strip();

    assertThat(markdown).isEqualTo("![diagram.png](" + DOWNLOAD_URL + ")");
  }
}
