package dev.nthings.adf4j.api;

import static dev.nthings.adf4j.ast.Adf.doc;
import static dev.nthings.adf4j.ast.Adf.extension;
import static dev.nthings.adf4j.ast.Adf.macroParameters;
import static dev.nthings.adf4j.ast.Adf.media;
import static dev.nthings.adf4j.ast.Adf.mediaSingle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.result.AdfException;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// The `AdfToMarkdown` entry points: every input form converts and analyzes the same way, failures
/// come back as diagnostics, and a converter is bound to its options.
class ConverterTests {

  private static final AdfToMarkdown CONVERTER = AdfToMarkdown.create();

  // A file media node with an id but no url, so the output depends on the media resolver.
  private static final String FILE_MEDIA =
      """
      {"type": "doc", "version": 1, "content": [
        {"type": "mediaSingle", "attrs": {"layout": "center"}, "content": [
          {"type": "media", "attrs": {"type": "file", "id": "abc-123", "alt": "diagram",
                                      "__fileName": "diagram.png"}}
        ]}
      ]}
      """;

  // A media node plus a viewpdf macro that names its attachment only by title.
  private static final String MEDIA_AND_MACRO =
      AdfJson.write(
          doc(
              mediaSingle(media("asset-1", "c")),
              extension(
                  "com.atlassian.confluence.macro.core",
                  "viewpdf",
                  macroParameters(Map.of("name", "guide.pdf")))));

  @Test
  void json_parse_results_and_documents_convert_identically() {
    var parsed = AdfJson.parse(MEDIA_AND_MACRO);

    var fromJson = CONVERTER.convert(MEDIA_AND_MACRO);

    assertThat(fromJson.isSuccess()).isTrue();
    assertThat(fromJson.body()).isEqualTo("[file](media:c/asset-1)\n\n\\[PDF: guide.pdf\\]");
    assertThat(CONVERTER.convert(parsed)).isEqualTo(fromJson);
    assertThat(CONVERTER.convert(parsed.document())).isEqualTo(fromJson);
    assertThat(CONVERTER.toMarkdown(MEDIA_AND_MACRO)).isEqualTo(fromJson.body());
  }

  @Test
  void blank_and_invalid_input_fail_with_an_empty_body_and_a_located_error() {
    var blank = CONVERTER.convert("   ");
    var notADocument = CONVERTER.convert("{\"type\":\"paragraph\",\"version\":1,\"content\":[]}");

    assertThat(blank.isSuccess()).isFalse();
    assertThat(blank.body()).isEmpty();
    assertThat(blank.metadata()).isEqualTo(ContentMetadata.empty());
    assertThat(blank.diagnostics())
        .extracting(Diagnostic::code)
        .containsExactly(Diagnostic.Code.EMPTY_INPUT);
    assertThat(CONVERTER.convert((String) null)).isEqualTo(blank);

    assertThat(notADocument.body()).isEmpty();
    assertThat(notADocument.diagnostics())
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue.code()).isEqualTo(Diagnostic.Code.INVALID_ROOT_NODE);
              assertThat(issue.paths()).containsExactly("/type");
            });
  }

  @Test
  void to_markdown_throws_the_failure_that_convert_reports() {
    assertThatExceptionOfType(AdfException.class)
        .isThrownBy(() -> CONVERTER.toMarkdown(null))
        .satisfies(
            failure ->
                assertThat(failure.diagnostics()).isEqualTo(CONVERTER.convert("").diagnostics()));
  }

  @Test
  void a_document_parsed_once_renders_repeatedly_under_different_options() {
    var parsed = AdfJson.parse(FILE_MEDIA);

    var was = withMediaResolver("v1/").convert(parsed);
    var is = withMediaResolver("v2/").convert(parsed);

    assertThat(was.body()).isEqualTo("![diagram](v1/abc-123)");
    assertThat(is.body()).isEqualTo("![diagram](v2/abc-123)");
  }

  @Test
  void a_parse_result_carries_its_warnings_into_each_render() {
    var parsed =
        AdfJson.parse(
            """
            {"type": "doc", "version": 2, "content": [
              {"type": "paragraph", "content": [{"type": "text", "text": "Hi"}]}
            ]}
            """);

    var result = CONVERTER.convert(parsed);

    assertThat(result.body()).isEqualTo("Hi");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.hasWarnings()).isTrue();
    assertThat(result.diagnostics())
        .isEqualTo(parsed.diagnostics())
        .extracting(Diagnostic::code)
        .containsExactly(Diagnostic.Code.UNSUPPORTED_VERSION);
  }

  @Test
  void an_invalid_parse_result_converts_and_analyzes_to_its_own_diagnostics() {
    var parsed = AdfJson.parse("{\"type\":\"paragraph\",\"version\":1,\"content\":[]}");

    var converted = CONVERTER.convert(parsed);
    var analyzed = CONVERTER.analyze(parsed);

    assertThat(converted.body()).isEmpty();
    assertThat(converted.diagnostics()).isEqualTo(parsed.diagnostics());
    assertThat(analyzed.diagnostics()).isEqualTo(parsed.diagnostics());
    assertThat(analyzed.metadata()).isEqualTo(ContentMetadata.empty());
  }

  @Test
  void analyze_reports_the_metadata_convert_would_without_rendering() {
    var resolverCalls = new java.util.concurrent.atomic.AtomicInteger();
    var converter =
        AdfToMarkdown.with(
            ConversionOptions.defaults()
                .withMediaResolver(
                    media -> {
                      resolverCalls.incrementAndGet();
                      return null;
                    }));

    var analyzed = converter.analyze(MEDIA_AND_MACRO);

    assertThat(resolverCalls).hasValue(0);
    assertThat(analyzed.metadata().referencedFileIds()).containsExactly("asset-1");
    assertThat(analyzed.metadata()).isEqualTo(converter.convert(MEDIA_AND_MACRO).metadata());
    assertThat(converter.analyze(AdfJson.parse(MEDIA_AND_MACRO).document())).isEqualTo(analyzed);
  }

  @Test
  void the_attachment_inventory_is_bound_to_the_converter() {
    var inventory =
        List.of(
            new AttachmentReference("asset-1", null, null, "url-a"),
            new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf"));
    var withInventory = AdfToMarkdown.with(ConversionOptions.defaults().withAttachments(inventory));

    var unknown = CONVERTER.analyze(MEDIA_AND_MACRO).metadata();
    var known = withInventory.analyze(MEDIA_AND_MACRO).metadata();

    assertThat(unknown.referencedFileIds()).containsExactly("asset-1");
    assertThat(unknown.attachmentRefs().getFirst().downloadUrl()).isNull();
    assertThat(known.referencedFileIds()).containsExactly("asset-1", "file-pdf-1");
    assertThat(known.attachmentRefs().getFirst().downloadUrl()).isEqualTo("url-a");
  }

  private static AdfToMarkdown withMediaResolver(String prefix) {
    return AdfToMarkdown.with(
        ConversionOptions.defaults().withMediaResolver(media -> prefix + media.id()));
  }
}
