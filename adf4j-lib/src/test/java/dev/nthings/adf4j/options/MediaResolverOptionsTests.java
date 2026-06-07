package dev.nthings.adf4j.options;

import dev.nthings.adf4j.AdfToMarkdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaResolverOptionsTests {

  // A mediaSingle wrapping a file media node carrying id + collection but no url.
  private static final String FILE_MEDIA =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "mediaSingle",
            "attrs": { "layout": "center" },
            "content": [
              {
                "type": "media",
                "attrs": {
                  "type": "file",
                  "id": "abc-123",
                  "collection": "contentId-42",
                  "alt": "diagram",
                  "__fileName": "diagram.png"
                }
              }
            ]
          }
        ]
      }
      """;

  // A file media node with no intrinsic type (no fileName/mediaType), as Confluence file media arrive: its
  // image-ness is known only from the path the resolver supplies.
  private static final String UNTYPED_FILE_MEDIA =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "mediaSingle",
            "attrs": { "layout": "center" },
            "content": [
              { "type": "media", "attrs": { "type": "file", "id": "abc-123", "alt": "diagram" } }
            ]
          }
        ]
      }
      """;

  // A file media node with no intrinsic label (no name/fileName/alt): its file name lives only in the
  // resolved path.
  private static final String UNLABELLED_FILE_MEDIA =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "mediaSingle",
            "attrs": { "layout": "center" },
            "content": [
              { "type": "media", "attrs": { "type": "file", "id": "abc-123", "collection": "c-42" } }
            ]
          }
        ]
      }
      """;

  @Test
  void file_link_takes_its_label_from_the_resolved_destinations_file_name() {
    var options = MarkdownOptions.defaults().withMediaResolver(attrs -> "attachments/report.pdf");
    var markdown = AdfToMarkdown.with(options).toMarkdown(UNLABELLED_FILE_MEDIA).strip();

    assertThat(markdown).isEqualTo("[report.pdf](attachments/report.pdf)");
  }

  @Test
  void file_link_falls_back_to_file_when_only_the_synthetic_placeholder_is_available() {
    var markdown = AdfToMarkdown.create().toMarkdown(UNLABELLED_FILE_MEDIA).strip();

    assertThat(markdown).isEqualTo("[file](media:c-42/abc-123)");
  }

  @Test
  void untyped_media_embeds_as_an_image_when_the_resolved_path_has_an_image_extension() {
    var options =
        MarkdownOptions.defaults().withMediaResolver(attrs -> "attachments/" + attrs.alt() + ".png");
    var markdown = AdfToMarkdown.with(options).toMarkdown(UNTYPED_FILE_MEDIA).strip();

    assertThat(markdown).isEqualTo("![diagram](attachments/diagram.png)");
  }

  @Test
  void untyped_media_renders_as_a_link_when_the_resolved_path_is_not_an_image() {
    var options = MarkdownOptions.defaults().withMediaResolver(attrs -> "attachments/report.pdf");
    var markdown = AdfToMarkdown.with(options).toMarkdown(UNTYPED_FILE_MEDIA).strip();

    assertThat(markdown).isEqualTo("[diagram](attachments/report.pdf)");
  }

  @Test
  void default_emits_the_synthetic_media_placeholder() {
    var markdown = AdfToMarkdown.create().toMarkdown(FILE_MEDIA).strip();

    assertThat(markdown).isEqualTo("![diagram](media:contentId-42/abc-123)");
  }

  @Test
  void resolver_turns_file_media_into_a_real_url() {
    var options =
        MarkdownOptions.defaults()
            .withMediaResolver(attrs -> "https://cdn.example.com/" + attrs.id());
    var markdown = AdfToMarkdown.with(options).toMarkdown(FILE_MEDIA).strip();

    assertThat(markdown).isEqualTo("![diagram](https://cdn.example.com/abc-123)");
  }

  @Test
  void resolver_returning_null_falls_back_to_the_placeholder() {
    var options = MarkdownOptions.defaults().withMediaResolver(attrs -> null);
    var markdown = AdfToMarkdown.with(options).toMarkdown(FILE_MEDIA).strip();

    assertThat(markdown).isEqualTo("![diagram](media:contentId-42/abc-123)");
  }

  @Test
  void resolver_that_throws_is_contained_and_falls_back_to_the_placeholder() {
    var options = MarkdownOptions.defaults().withMediaResolver(attrs -> {
      throw new RuntimeException("boom");
    });
    var markdown = AdfToMarkdown.with(options).toMarkdown(FILE_MEDIA).strip();

    assertThat(markdown).isEqualTo("![diagram](media:contentId-42/abc-123)");
  }
}
