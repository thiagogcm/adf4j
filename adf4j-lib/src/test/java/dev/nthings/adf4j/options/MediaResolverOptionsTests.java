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
                  "alt": "diagram"
                }
              }
            ]
          }
        ]
      }
      """;

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
}
