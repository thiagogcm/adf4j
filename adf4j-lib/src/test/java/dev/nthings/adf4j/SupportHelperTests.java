package dev.nthings.adf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Anchor;
import dev.nthings.adf4j.metadata.AttachmentReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

class SupportHelperTests {

  private static final List<Arguments> confluence_page_id_urls =
      List.of(
          argumentSet(
              "canonical page URL",
              new PageIdCase("https://example.atlassian.net/wiki/spaces/OF/pages/12345", "12345")),
          argumentSet(
              "edit URL",
              new PageIdCase(
                  "https://example.atlassian.net/wiki/spaces/OF/pages/edit-v2/67890?draftShareId=abc",
                  "67890")),
          argumentSet(
              "relative URL with fragment",
              new PageIdCase("/wiki/spaces/OF/pages/54321/Runbook#details", "54321")),
          argumentSet("external URL", new PageIdCase("https://external.example.com/docs", null)));
  private static final List<Arguments> anchor_macro_shapes =
      List.of(
          argumentSet(
              "explicit unnamed anchor parameter wins",
              new AnchorCase(
                  """
                  {
                    "": {
                      "value": "custom-anchor"
                    },
                    "legacyAnchorId": {
                      "value": "legacy-anchor"
                    }
                  }
                  """,
                  "custom-anchor")),
          argumentSet(
              "legacy anchor id is accepted",
              new AnchorCase(
                  """
                  {
                    "legacyAnchorId": {
                      "value": "legacy-anchor"
                    }
                  }
                  """,
                  "legacy-anchor")));

  private static final List<Arguments> media_type_inference =
      List.of(
          argumentSet("plain filename", new MediaTypeCase("diagram.png", "image/png")),
          argumentSet(
              "clean url", new MediaTypeCase("https://example.com/diagram.png", "image/png")),
          argumentSet(
              "url with query string",
              new MediaTypeCase("https://example.com/diagram.png?width=200&v=2", "image/png")),
          argumentSet(
              "url with fragment",
              new MediaTypeCase("https://example.com/diagram.png#fig1", "image/png")),
          argumentSet(
              "pdf url with query string",
              new MediaTypeCase("https://example.com/report.pdf?download=true", "application/pdf")),
          argumentSet(
              "extension only in query string is ignored",
              new MediaTypeCase("https://cdn.example.com/media/abc123?token=x.pdf", null)),
          argumentSet(
              "url without an extension",
              new MediaTypeCase("https://cdn.example.com/v1.2/photo", null)));

  private final AdfTestSupport testSupport = AdfTestSupport.create();

  record PageIdCase(String url, String pageId) {}

  record AnchorCase(String macroParamsJson, String anchorId) {}

  record MediaTypeCase(String fileNameOrUrl, String mediaType) {}

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("confluence_page_id_urls")
  void confluence_support_recognizes_supported_page_urls(PageIdCase input) {
    assertThat(ConfluenceSupport.pageId(input.url())).isEqualTo(input.pageId());
  }

  @Test
  void attachment_references_prefers_normalized_title_matches_over_raw_ids() throws Exception {
    var inventory =
        AttachmentInventory.from(
            List.of(new AttachmentReference("resolved-id", "Guide.PDF", "application/pdf")));
    var macroParams =
        testSupport.macroParams(
            """
            {
              "name": {
                "value": " guide.pdf "
              },
              "fileId": {
                "value": "fallback-id"
              }
            }
            """);

    var resolved = AttachmentReferences.resolve(macroParams, inventory);

    assertThat(resolved.fileId()).isEqualTo("resolved-id");
    assertThat(resolved.title()).isEqualTo("Guide.PDF");
    assertThat(resolved.mediaType()).isEqualTo("application/pdf");
  }

  @Test
  void attachment_references_prefer_the_file_id_when_titles_collide() throws Exception {
    var inventory =
        AttachmentInventory.from(
            List.of(
                new AttachmentReference("first", "report.pdf", "application/pdf"),
                new AttachmentReference("second", "report.pdf", "application/pdf")));
    var macroParams =
        testSupport.macroParams(
            """
            { "name": { "value": "report.pdf" }, "fileId": { "value": "second" } }
            """);

    assertThat(AttachmentReferences.resolve(macroParams, inventory).fileId()).isEqualTo("second");
  }

  @Test
  void attachment_references_give_a_title_less_entry_the_macro_name() throws Exception {
    var inventory =
        AttachmentInventory.from(
            List.of(new AttachmentReference("file-1", null, null, "https://cdn.example.com/1")));
    var macroParams =
        testSupport.macroParams(
            """
            { "name": { "value": "guide.pdf" }, "fileId": { "value": "file-1" } }
            """);

    assertThat(AttachmentReferences.resolve(macroParams, inventory))
        .isEqualTo(
            new AttachmentReference(
                "file-1", "guide.pdf", "application/pdf", "https://cdn.example.com/1"));
  }

  @Test
  void attachment_references_falls_back_to_attachment_ids_and_inferred_media_types()
      throws Exception {
    var macroParams =
        testSupport.macroParams(
            """
            {
              "name": {
                "value": "diagram.png"
              },
              "attachmentId": {
                "value": "file-99"
              }
            }
            """);

    var resolved = AttachmentReferences.resolve(macroParams, AttachmentInventory.from(null));

    assertThat(resolved.fileId()).isEqualTo("file-99");
    assertThat(resolved.title()).isEqualTo("diagram.png");
    assertThat(resolved.mediaType()).isEqualTo("image/png");
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("anchor_macro_shapes")
  void confluence_macro_resolves_supported_anchor_macro_shapes(AnchorCase input) throws Exception {
    var macroParams = testSupport.attributes(input.macroParamsJson()).values();
    var extension =
        new Extension(
            new Attributes(
                Map.of(
                    "extensionType", "com.atlassian.confluence.macro.core",
                    "extensionKey", "anchor",
                    "parameters", Map.of("macroParams", macroParams))),
            List.of());

    assertThat(ConfluenceMacro.of(extension)).isEqualTo(new Anchor(input.anchorId()));
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("media_type_inference")
  void infer_media_type_reads_the_extension_from_a_url_path_only(MediaTypeCase input) {
    assertThat(AttachmentReferences.inferMediaType(input.fileNameOrUrl()))
        .isEqualTo(input.mediaType());
  }
}
