package dev.nthings.adf4j;

import dev.nthings.adf4j.ast.MediaAttrs;
import dev.nthings.adf4j.options.MarkdownOptions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InlineMediaImageRenderingTests {

  // The editor-migration macro shape observed in the wild: the media identity sits directly under
  // parameters, not under macroParams.
  private static final String INLINE_MEDIA_IMAGE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              { "type": "text", "text": "logo " },
              {
                "type": "inlineExtension",
                "attrs": {
                  "extensionType": "com.atlassian.confluence.migration",
                  "extensionKey": "inline-media-image",
                  "parameters": {
                    "id": "7e62545e-7cdc-42a6-8760-137006ae963b",
                    "collection": "contentId-326931860",
                    "width": "20",
                    "height": ""
                  }
                }
              }
            ]
          }
        ]
      }
      """;

  private static final String INLINE_MEDIA_IMAGE_WITHOUT_ID =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.migration",
              "extensionKey": "inline-media-image",
              "parameters": { "width": "20" }
            }
          }
        ]
      }
      """;

  @Test
  void without_a_resolver_it_renders_an_image_with_the_media_placeholder() {
    var result = AdfToMarkdown.create().convert(INLINE_MEDIA_IMAGE);

    assertThat(result.body()).isEqualTo(
        "logo ![media](media:contentId-326931860/7e62545e-7cdc-42a6-8760-137006ae963b)");
    assertThat(result.diagnostics()).noneMatch(d -> "UNSUPPORTED_MACRO".equals(d.code()));
  }

  @Test
  void a_media_resolver_turns_it_into_a_local_image_link() {
    var seen = new MediaAttrs[1];
    var options = MarkdownOptions.defaults().withMediaResolver(attrs -> {
      seen[0] = attrs;
      return "images/logo.png";
    });

    var result = AdfToMarkdown.with(options).convert(INLINE_MEDIA_IMAGE);

    assertThat(result.body()).isEqualTo("logo ![media](images/logo.png)");
    assertThat(seen[0].id()).isEqualTo("7e62545e-7cdc-42a6-8760-137006ae963b");
    assertThat(seen[0].collection()).isEqualTo("contentId-326931860");
    assertThat(seen[0].mediaType()).isEqualTo("image");
  }

  @Test
  void the_media_id_is_referenced_in_metadata_like_a_media_node() {
    var metadata = AdfToMarkdown.create().analyze(INLINE_MEDIA_IMAGE);

    assertThat(metadata.referencedFileIds())
        .containsExactly("7e62545e-7cdc-42a6-8760-137006ae963b");
    assertThat(metadata.attachmentRefs())
        .singleElement()
        .satisfies(reference -> assertThat(reference.mediaType()).isEqualTo("image"));
  }

  @Test
  void without_a_media_id_the_macro_keeps_the_generic_placeholder() {
    var result = AdfToMarkdown.create().convert(INLINE_MEDIA_IMAGE_WITHOUT_ID);

    assertThat(result.body())
        .contains("\\[Extension: com.atlassian.confluence.migration/inline-media-image\\]");
    assertThat(result.diagnostics()).anyMatch(d -> "UNSUPPORTED_MACRO".equals(d.code()));
  }
}
