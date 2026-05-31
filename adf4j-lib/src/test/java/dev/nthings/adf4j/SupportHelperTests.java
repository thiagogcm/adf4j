package dev.nthings.adf4j;

import java.util.List;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.metadata.AttachmentReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

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
          argumentSet(
              "external URL",
              new PageIdCase("https://external.example.com/docs", null)));
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

  private final AdfTestSupport testSupport = AdfTestSupport.create();

  record PageIdCase(String url, String pageId) {}

  record AnchorCase(String macroParamsJson, String anchorId) {}

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("confluence_page_id_urls")
  void confluence_support_recognizes_supported_page_urls(PageIdCase input) {
    assertThat(ConfluenceSupport.pageId(input.url())).isEqualTo(input.pageId());
  }

  @Test
  void attachment_references_prefers_normalized_title_matches_over_raw_ids()
      throws Exception {
    var context =
        ConfluenceRenderContext.empty()
            .withAttachmentReferences(
                List.of(
                    new AttachmentReference("resolved-id", "Guide.PDF", "application/pdf")));
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

    var resolved =
        AttachmentReferences.resolve(macroParams, context.attachmentReferencesByTitle());

    assertThat(resolved.fileId()).isEqualTo("resolved-id");
    assertThat(resolved.title()).isEqualTo("Guide.PDF");
    assertThat(resolved.mediaType()).isEqualTo("application/pdf");
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

    var resolved = AttachmentReferences.resolve(macroParams, null);

    assertThat(resolved.fileId()).isEqualTo("file-99");
    assertThat(resolved.title()).isEqualTo("diagram.png");
    assertThat(resolved.mediaType()).isEqualTo("image/png");
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("anchor_macro_shapes")
  void confluence_support_resolves_supported_anchor_macro_shapes(AnchorCase input) throws Exception {
    assertThat(ConfluenceSupport.anchorId(testSupport.macroParams(input.macroParamsJson())))
        .isEqualTo(input.anchorId());
  }
}
