package dev.nthings.adf4j;

import dev.nthings.adf4j.options.MarkdownOptions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PerCallOptionsTests {

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
  void per_call_options_override_the_bound_options_on_a_reused_converter() {
    // One converter, reused; the heavy pipeline is built once.
    var converter = AdfToMarkdown.create();

    var firstPage =
        MarkdownOptions.defaults().withMediaResolver(attrs -> "pages/1/" + attrs.id() + ".png");
    var secondPage =
        MarkdownOptions.defaults().withMediaResolver(attrs -> "pages/2/" + attrs.id() + ".png");

    assertThat(converter.toMarkdown(FILE_MEDIA, firstPage).strip())
        .isEqualTo("![diagram](pages/1/abc-123.png)");
    assertThat(converter.toMarkdown(FILE_MEDIA, secondPage).strip())
        .isEqualTo("![diagram](pages/2/abc-123.png)");
  }

  @Test
  void per_call_options_do_not_disturb_the_bound_options() {
    var converter =
        AdfToMarkdown.with(
            MarkdownOptions.defaults().withMediaResolver(attrs -> "bound/" + attrs.id()));
    var perCall = MarkdownOptions.defaults().withMediaResolver(attrs -> "percall/" + attrs.id());

    assertThat(converter.toMarkdown(FILE_MEDIA, perCall).strip())
        .isEqualTo("![diagram](percall/abc-123)");
    // The bound options still drive the single-argument overload.
    assertThat(converter.toMarkdown(FILE_MEDIA).strip())
        .isEqualTo("![diagram](bound/abc-123)");
  }

  @Test
  void a_document_parsed_once_renders_repeatedly_under_different_per_call_options() {
    var converter = AdfToMarkdown.create();
    var parsed = converter.parse(FILE_MEDIA);

    var was = converter.convert(
        parsed, MarkdownOptions.defaults().withMediaResolver(attrs -> "v1/" + attrs.id()));
    var is = converter.convert(
        parsed, MarkdownOptions.defaults().withMediaResolver(attrs -> "v2/" + attrs.id()));

    assertThat(was.body().strip()).isEqualTo("![diagram](v1/abc-123)");
    assertThat(is.body().strip()).isEqualTo("![diagram](v2/abc-123)");
  }

  @Test
  void converting_a_parse_result_carries_its_parse_issues_into_each_render() {
    var converter = AdfToMarkdown.create();
    // Version 2 parses best-effort with an UNSUPPORTED_VERSION warning.
    var parsed = converter.parse(
        "{\"type\":\"doc\",\"version\":2,\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}]}]}");

    var result = converter.convert(parsed);

    assertThat(result.body()).isEqualTo("Hi");
    assertThat(result.diagnostics()).isEqualTo(parsed.issues());
  }

  @Test
  void converting_an_invalid_parse_result_yields_an_empty_body_with_the_issues_preserved() {
    var converter = AdfToMarkdown.create();
    var parsed = converter.parse("{\"type\":\"paragraph\",\"version\":1,\"content\":[]}");

    var result = converter.convert(parsed);

    assertThat(result.body()).isEmpty();
    assertThat(result.diagnostics()).isEqualTo(parsed.issues()).isNotEmpty();
  }
}
