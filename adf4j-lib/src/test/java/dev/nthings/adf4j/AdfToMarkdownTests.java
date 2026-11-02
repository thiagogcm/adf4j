package dev.nthings.adf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.ParseResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdfToMarkdownTests {

  private static final AdfToMarkdown PROCESSOR = AdfToMarkdown.create();

  // A file media node carrying an id but no url, so the output depends on the media resolver.
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
              { "type": "media", "attrs": { "type": "file", "id": "abc-123", "alt": "diagram", "__fileName": "diagram.png" } }
            ]
          }
        ]
      }
      """;

  @Test
  void default_construction_exposes_the_public_parse_and_render_workflow() {
    var markdown =
        PROCESSOR.toMarkdown(
            """
            {
              "type": "doc",
              "version": 1,
              "content": [
                {
                  "type": "paragraph",
                  "content": [
                    {
                      "type": "text",
                      "text": "Hello, ADF"
                    }
                  ]
                }
              ]
            }
            """);

    assertThat(markdown).isEqualTo("Hello, ADF");
    assertThat(AdfJson.parse("{\"type\":\"doc\",\"version\":1,\"content\":[]}").isValid()).isTrue();
  }

  @Test
  void with_rejects_null_options() {
    assertThatNullPointerException()
        .isThrownBy(() -> AdfToMarkdown.with(null))
        .withMessage("options");
  }

  @Test
  void parsed_inputs_reject_null() {
    assertThatNullPointerException().isThrownBy(() -> PROCESSOR.convert((ParseResult) null));
    assertThatNullPointerException().isThrownBy(() -> PROCESSOR.convert((AdfDocument) null));
    assertThatNullPointerException().isThrownBy(() -> PROCESSOR.convert((ExcerptDefinition) null));
    assertThatNullPointerException().isThrownBy(() -> PROCESSOR.analyze((ParseResult) null));
    assertThatNullPointerException().isThrownBy(() -> PROCESSOR.analyze((AdfDocument) null));
  }

  @Test
  void null_json_converts_to_an_empty_result() {
    assertThat(PROCESSOR.convert((String) null).body()).isEmpty();
    assertThat(PROCESSOR.toMarkdown(null)).isEmpty();
  }

  @Test
  void a_document_parsed_once_renders_repeatedly_under_different_options() {
    var parsed = AdfJson.parse(FILE_MEDIA);

    var was =
        AdfToMarkdown.with(
                MarkdownOptions.defaults().withMediaResolver(media -> "v1/" + media.id()))
            .convert(parsed);
    var is =
        AdfToMarkdown.with(
                MarkdownOptions.defaults().withMediaResolver(media -> "v2/" + media.id()))
            .convert(parsed);

    assertThat(was.body().strip()).isEqualTo("![diagram](v1/abc-123)");
    assertThat(is.body().strip()).isEqualTo("![diagram](v2/abc-123)");
  }

  @Test
  void converting_a_parse_result_carries_its_parse_diagnostics_into_each_render() {
    // Version 2 parses best-effort with an UNSUPPORTED_VERSION warning.
    var parsed =
        AdfJson.parse(
            "{\"type\":\"doc\",\"version\":2,\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}]}]}");

    var result = PROCESSOR.convert(parsed);

    assertThat(result.body()).isEqualTo("Hi");
    assertThat(result.diagnostics()).isEqualTo(parsed.diagnostics());
  }

  @Test
  void converting_an_invalid_parse_result_yields_an_empty_body_with_its_diagnostics() {
    var parsed = AdfJson.parse("{\"type\":\"paragraph\",\"version\":1,\"content\":[]}");

    var result = PROCESSOR.convert(parsed);

    assertThat(result.body()).isEmpty();
    assertThat(result.diagnostics()).isEqualTo(parsed.diagnostics()).isNotEmpty();
    assertThat(PROCESSOR.analyze(parsed)).isEqualTo(result.metadata());
  }

  @Test
  void converters_share_no_analysis_between_options() {
    var document = AdfJson.parse(FILE_MEDIA).document();
    var inventory = List.of(new AttachmentReference("abc-123", null, null, "url-a"));

    var unknown = PROCESSOR.analyze(document);
    var known =
        AdfToMarkdown.with(MarkdownOptions.defaults().withAttachments(inventory)).analyze(document);

    assertThat(unknown.attachmentRefs().getFirst().downloadUrl()).isNull();
    assertThat(known.attachmentRefs().getFirst().downloadUrl()).isEqualTo("url-a");
  }
}
