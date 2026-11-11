package dev.nthings.adf4j.api;

import static dev.nthings.adf4j.ast.Adf.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.AdfValidation;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.ExtensionOutput;
import dev.nthings.adf4j.options.PageTreeEntry;
import dev.nthings.adf4j.result.AdfException;
import dev.nthings.adf4j.result.ConversionEffect;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// End-to-end workflows a caller strings together: resolving files, replacing extensions, portable
/// options, and authoring documents that must validate before submission.
class WorkflowTests {
  @Test
  void one_file_resolver_serves_media_and_attachment_macros() {
    var options =
        ConversionOptions.defaults()
            .withAttachments(
                List.of(new AttachmentReference("file-1", "guide.pdf", "application/pdf")))
            .withFileResolver(id -> "downloads/" + id);
    var doc =
        doc(
            mediaSingle(media("file-1", "collection")),
            extension(
                "com.atlassian.confluence.macro.core",
                "viewpdf",
                macroParameters(Map.of("name", "guide.pdf"))));
    var result = AdfToMarkdown.with(options).convert(doc);
    assertThat(result.body())
        .isEqualTo(
            """
            [guide.pdf](downloads/file-1)

            [PDF: guide.pdf](downloads/file-1)""");
    assertThat(result.unresolved().isEmpty()).isTrue();
  }

  @Test
  void failed_file_lookup_is_actionable_and_inventory_can_still_rescue_the_output() {
    var doc = doc(paragraph(mediaInline("file-1", "collection")));
    var options =
        ConversionOptions.defaults()
            .withFileResolver(
                id -> {
                  throw new IllegalStateException("offline");
                });
    var result = AdfToMarkdown.with(options).convert(doc);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.code()).isEqualTo(Diagnostic.Code.CALLBACK_FAILED);
              assertThat(d.paths()).containsExactly("/content/0/content/0");
            });
    assertThat(result.unresolved())
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.id()).isEqualTo("file-1");
              assertThat(r.reason()).isEqualTo(UnresolvedResource.Reason.CALLBACK_FAILED);
              assertThat(r.fallback()).isEqualTo("media:collection/file-1");
            });
    var rescued =
        AdfToMarkdown.with(
                options.withAttachments(
                    List.of(
                        new AttachmentReference(
                            "file-1", "guide.pdf", null, "downloads/guide.pdf"))))
            .convert(doc);
    assertThat(rescued.body()).isEqualTo("[guide.pdf](downloads/guide.pdf)");
    assertThat(rescued.unresolved().isEmpty()).isTrue();
  }

  @Test
  void replacing_an_extension_owns_its_body_but_analysis_still_describes_the_source() {
    var doc = doc(bodiedExtension("app", "report", paragraph(mediaInline("file-1", "collection"))));
    var converter =
        AdfToMarkdown.with(
            ConversionOptions.defaults()
                .withExtensionRenderers(
                    List.of(node -> ExtensionOutput.replace("Report unavailable"))));
    var result = converter.convert(doc);
    assertThat(result.body()).isEqualTo("Report unavailable");
    assertThat(result.unresolved().isEmpty()).isTrue();
    assertThat(result.effects()).isEmpty();
    assertThat(converter.analyze(doc).metadata().referencedFileIds()).containsExactly("file-1");
  }

  @Test
  void portable_options_distinguish_presentation_changes_from_warnings() {
    var options =
        ConversionOptions.fromJson(
            """
    {"markdown":{"tableFallback":"gfm-empty-header"},"context":{"title":"Report"}}
    """);
    var doc = doc(table(tableRow(tableCell("first")), tableRow(tableCell("second"))));
    var result = AdfToMarkdown.with(options).convert(doc);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.hasWarnings()).isFalse();
    assertThat(result.body())
        .isEqualTo(
            """
            # Report

            |        |
            | ------ |
            | first  |
            | second |""");
    assertThat(result.effects())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.kind()).isEqualTo(ConversionEffect.Kind.STRUCTURE_CHANGED);
              assertThat(e.path()).isEqualTo("/content/0");
            });
  }

  @Test
  void a_failed_page_lookup_does_not_taint_other_entries_in_the_same_tree() {
    var options =
        ConversionOptions.defaults()
            .withPageTreeResolver(
                ref ->
                    List.of(
                        new PageTreeEntry(0, "Offline", "1"),
                        new PageTreeEntry(0, "Missing", "2"),
                        new PageTreeEntry(0, "Available", "3")))
            .withPageLinkResolver(
                id -> {
                  if (id.equals("1")) throw new IllegalStateException("offline");
                  return id.equals("3") ? "pages/3" : null;
                });
    var result =
        AdfToMarkdown.with(options)
            .convert(doc(extension("com.atlassian.confluence.macro.core", "pagetree")));
    assertThat(result.body())
        .isEqualTo(
            """
            - Offline
            - Missing
            - [Available](pages/3)""");
    assertThat(result.unresolved())
        .extracting(UnresolvedResource::reason)
        .containsExactly(
            UnresolvedResource.Reason.CALLBACK_FAILED, UnresolvedResource.Reason.NO_ANSWER);
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(d -> assertThat(d.paths()).containsExactly("/content/0"));
  }

  @Test
  void editing_a_parsed_document_can_be_checked_before_submission() {
    var input = AdfJson.write(doc(panel(PanelType.INFO, paragraph("Read this"))));
    var parsed = AdfJson.parse(input).requireDocument();
    var edited = parsed.append(paragraph("Reviewed"));
    assertThat(AdfValidation.validate(edited).isSuccess()).isTrue();

    var invalid =
        doc(panel(PanelType.INFO, paragraph("Read this").withMarks(alignment(TextAlign.CENTER))));
    var json = AdfJson.write(invalid);
    // The parser must preserve imported data, even when a product would reject it.
    assertThat(AdfJson.write(AdfJson.parse(json).requireDocument())).isEqualTo(json);
    var validation = AdfValidation.validate(json);
    assertThat(validation.isSuccess()).isFalse();
    assertThat(validation.diagnostics())
        .anySatisfy(d -> assertThat(d.paths()).contains("/content/0/content/0/marks"));
    assertThatThrownBy(() -> AdfValidation.requireValid(invalid)).isInstanceOf(AdfException.class);
  }

  @Test
  void authoring_reports_invalid_heading_and_mark_inputs_at_the_call_site() {
    assertThatThrownBy(() -> heading(9, "Title"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("level");
    assertThatThrownBy(() -> text("code", code(), strong()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("code");
    assertThat(
            AdfValidation.validate(
                    doc(paragraph(text("code", code(), link("https://example.com")))))
                .isSuccess())
        .isTrue();
  }
}
