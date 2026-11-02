package dev.nthings.adf4j;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.MarkdownOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyzeApiTests {

  // A file media node carrying an id, plus a viewpdf attachment macro referenced only by title.
  private static final String MEDIA_AND_MACRO =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "mediaSingle",
            "attrs": { "layout": "center" },
            "content": [
              { "type": "media", "attrs": { "type": "file", "id": "asset-1", "alt": "diagram" } }
            ]
          },
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

  @Test
  void analyze_extracts_referenced_file_ids_without_rendering() {
    var metadata = AdfToMarkdown.create().analyze(AdfJson.parse(MEDIA_AND_MACRO));

    // Media ids are collected unconditionally; the macro id is absent without an attachment
    // context.
    assertThat(metadata.referencedFileIds()).containsExactly("asset-1");
  }

  @Test
  void analyze_resolves_macro_attachments_through_the_supplied_context() {
    var options =
        MarkdownOptions.defaults()
            .withAttachments(
                List.of(new AttachmentReference("file-pdf-1", "guide.pdf", "application/pdf")));

    var metadata = AdfToMarkdown.with(options).analyze(AdfJson.parse(MEDIA_AND_MACRO));

    // With the context seeded, the macro's attachment is now part of the referenced set.
    assertThat(metadata.referencedFileIds()).containsExactly("asset-1", "file-pdf-1");
  }

  @Test
  void analyze_matches_the_metadata_a_full_convert_would_produce() {
    var converter = AdfToMarkdown.create();

    assertThat(converter.analyze(AdfJson.parse(MEDIA_AND_MACRO)))
        .isEqualTo(converter.convert(MEDIA_AND_MACRO).metadata());
  }

  @Test
  void analyze_returns_empty_metadata_for_blank_or_invalid_input() {
    var converter = AdfToMarkdown.create();

    assertThat(converter.analyze(AdfJson.parse("   ")).referencedFileIds()).isEmpty();
    assertThat(converter.analyze(AdfJson.parse("{\"type\":\"paragraph\"}")).referencedFileIds())
        .isEmpty();
  }

  @Test
  void analyze_reads_a_parsed_document_directly() {
    var parsed = AdfJson.parse(MEDIA_AND_MACRO);

    assertThat(AdfToMarkdown.create().analyze(parsed.document()))
        .isEqualTo(AdfToMarkdown.create().analyze(parsed));
  }
}
