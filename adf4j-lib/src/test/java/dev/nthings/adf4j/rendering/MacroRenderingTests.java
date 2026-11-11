package dev.nthings.adf4j.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.AdfMedia;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.ExtensionOutput;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.testing.TestResources;
import java.util.List;
import org.junit.jupiter.api.Test;

/// Confluence macros and extensions: bodied and sync blocks, excerpts, page trees, TOC, charts,
/// viewpdf and inline media images.
class MacroRenderingTests {

  private static final AdfToMarkdown processor = AdfToMarkdown.create();

  // The legacy chart macro: a bodied extension whose body holds the data table.
  private static final String LEGACY_BODIED_CHART =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "bodiedExtension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "chart",
              "parameters": { "macroParams": { "title": { "value": "Monthly visits" } } }
            },
            "content": [
              {
                "type": "table",
                "content": [
                  {
                    "type": "tableRow",
                    "content": [
                      { "type": "tableHeader", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "Month" } ] } ] },
                      { "type": "tableHeader", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "Visits" } ] } ] }
                    ]
                  },
                  {
                    "type": "tableRow",
                    "content": [
                      { "type": "tableCell", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "January" } ] } ] },
                      { "type": "tableCell", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "42" } ] } ] }
                    ]
                  }
                ]
              }
            ]
          }
        ]
      }
      """;

  // Modern chart shape: bodyless extension; the data table sits elsewhere via a dataConsumer.
  private static final String MODERN_CHART =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.chart",
              "extensionKey": "chart:default",
              "parameters": {
                "dataConsumerFallback": [ "5433c6f2-9c62-4bab-89ba-ad6101434e9d" ],
                "chartType": "BAR",
                "chartGroup": {
                  "customizeTab": {
                    "titlesField": { "chartTitle": "Resoluções por mês", "yLabel": "", "xLabel": "" }
                  }
                },
                "extensionTitle": "Gráfico"
              }
            },
            "marks": [
              { "type": "dataConsumer", "attrs": { "sources": [ "5433c6f2-9c62-4bab-89ba-ad6101434e9d" ] } }
            ]
          }
        ]
      }
      """;

  private static final String MODERN_CHART_WITHOUT_TITLE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.chart",
              "extensionKey": "chart:default",
              "parameters": { "chartType": "PIE" }
            }
          }
        ]
      }
      """;

  // The legacy bodyless variant: no data anywhere in the document.
  private static final String LEGACY_BODYLESS_CHART =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "chart:default",
              "parameters": { "macroParams": { "title": { "value": "Monthly metrics" } } }
            }
          }
        ]
      }
      """;

  // Editor-migration shape: media identity sits directly under parameters, not macroParams.
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
  void convert_resolves_db_derived_viewpdf_cases_with_attachment_context() throws Exception {
    var options =
        ConversionOptions.defaults()
            .withAttachments(
                List.of(
                    new AttachmentReference(
                        "file-pdf-123",
                        "Open_Finance_cadastro_diretorio_passo_a_passo.pdf",
                        "application/pdf")));

    var result =
        AdfToMarkdown.with(options).convert(TestResources.spec("lista-participantes-viewpdf"));

    assertThat(result.body())
        .contains(
            "[PDF: Open_Finance_cadastro_diretorio_passo_a_passo.pdf](attachment:file-pdf-123)");
    assertThat(result.metadata().attachmentRefs())
        .singleElement()
        .satisfies(
            ref -> {
              assertThat(ref.fileId()).isEqualTo("file-pdf-123");
              assertThat(ref.title())
                  .isEqualTo("Open_Finance_cadastro_diretorio_passo_a_passo.pdf");
            });
  }

  @Test
  void convert_renders_bodied_extension_header_then_body_and_escapes_marker_text() {
    // bodiedExtension needs a body in the local schema; its attribute-derived text must not
    // become a Markdown heading.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "bodiedExtension",
              "attrs": {
                "extensionType": "com.example.macros",
                "extensionKey": "callout",
                "text": "# Important"
              },
              "content": [
                { "type": "paragraph", "content": [{ "type": "text", "text": "Body text" }] }
              ]
            }
          ]
        }
        """;

    var markdown = processor.toMarkdown(adf);

    assertThat(markdown).isEqualToNormalizingNewlines("\\# Important\n\nBody text");
  }

  @Test
  void convert_renders_confluence_excerpt_bodied_extension_as_body_only() {
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "bodiedExtension",
              "attrs": {
                "extensionType": "com.atlassian.confluence.macro.core",
                "extensionKey": "excerpt",
                "text": "Excerpt wrapper"
              },
              "content": [
                { "type": "paragraph", "content": [{ "type": "text", "text": "Only this survives" }] }
              ]
            }
          ]
        }
        """;

    var markdown = processor.toMarkdown(adf);

    assertThat(markdown).isEqualTo("Only this survives");
  }

  @Test
  void convert_renders_bodied_sync_block_label_then_body() {
    // Schema requires resourceId/localId on sync blocks: keep the id visible, salvage the body.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "bodiedSyncBlock",
              "attrs": { "resourceId": "abc-123", "localId": "sync-1" },
              "content": [
                { "type": "paragraph", "content": [{ "type": "text", "text": "Synced paragraph" }] }
              ]
            }
          ]
        }
        """;

    var markdown = processor.toMarkdown(adf);

    assertThat(markdown)
        .isEqualToNormalizingNewlines("\\[Sync block: abc-123\\]\n\nSynced paragraph");
  }

  @Test
  void convert_escapes_a_toc_anchor_containing_spaces() {
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "extension",
              "attrs": {
                "extensionType": "com.atlassian.confluence.macro.core",
                "extensionKey": "toc",
                "parameters": { "macroParams": { "minLevel": { "value": "1" }, "maxLevel": { "value": "6" } } }
              }
            },
            {
              "type": "heading",
              "attrs": { "level": 2 },
              "content": [
                { "type": "text", "text": "H" },
                {
                  "type": "inlineExtension",
                  "attrs": {
                    "extensionType": "com.atlassian.confluence.macro.core",
                    "extensionKey": "anchor",
                    "parameters": { "macroParams": { "": { "value": "a b" } } }
                  }
                }
              ]
            }
          ]
        }
        """;

    assertThat(processor.toMarkdown(adf)).contains("(<#a b>)");
  }

  @Test
  void convert_renders_a_pagetree_macro_as_a_placeholder() {
    assertThat(processor.toMarkdown(pageTreeMacro(null))).isEqualTo("{{pagetree}}");
  }

  @Test
  void convert_treats_a_self_rooted_pagetree_as_the_bare_placeholder() {
    assertThat(processor.toMarkdown(pageTreeMacro("@self"))).isEqualTo("{{pagetree}}");
  }

  @Test
  void convert_surfaces_a_named_pagetree_root() {
    assertThat(processor.toMarkdown(pageTreeMacro("Getting Started")))
        .isEqualTo("{{pagetree:Getting Started}}");
  }

  @Test
  void convert_keeps_a_named_pagetree_root_token_well_formed() {
    // Newlines/braces in the title must not break out of the {{pagetree:…}} token.
    assertThat(processor.toMarkdown(pageTreeMacro("a {b}\\nc"))).isEqualTo("{{pagetree:a b c}}");
  }

  @Test
  void a_bodied_chart_renders_an_italic_caption_and_its_data_table() {
    var result = AdfToMarkdown.create().convert(LEGACY_BODIED_CHART);

    assertThat(result.body())
        .isEqualTo(
            """
        *Chart: Monthly visits*

        | Month   | Visits |
        | ------- | ------ |
        | January | 42     |""");
    assertThat(result.diagnostics()).noneMatch(d -> d.code() == Diagnostic.Code.UNSUPPORTED_MACRO);
  }

  @Test
  void a_modern_chart_renders_an_italic_caption_from_its_nested_title() {
    var result = AdfToMarkdown.create().convert(MODERN_CHART);

    // The table is a separate node already rendered in place: the chart adds only the caption.
    assertThat(result.body()).isEqualTo("*Chart: Resoluções por mês*");
    assertThat(result.diagnostics()).noneMatch(d -> d.code() == Diagnostic.Code.UNSUPPORTED_MACRO);
  }

  @Test
  void a_modern_chart_without_a_title_renders_a_bare_caption() {
    var result = AdfToMarkdown.create().convert(MODERN_CHART_WITHOUT_TITLE);

    assertThat(result.body()).isEqualTo("*Chart*");
  }

  @Test
  void a_legacy_bodyless_chart_keeps_its_labelled_placeholder() {
    var markdown = AdfToMarkdown.create().toMarkdown(LEGACY_BODYLESS_CHART).strip();

    assertThat(markdown).isEqualTo("\\[Chart: Monthly metrics\\]");
  }

  @Test
  void a_custom_extension_renderer_still_overrides_the_chart_caption() {
    var options =
        ConversionOptions.defaults()
            .withExtensionRenderers(
                List.of(
                    extension ->
                        "com.atlassian.chart".equals(extension.extensionType())
                            ? ExtensionOutput.replace(
                                "custom chart block ("
                                    + extension.parameters().string("extensionTitle")
                                    + ")")
                            : ExtensionOutput.defer()));

    var markdown = AdfToMarkdown.with(options).toMarkdown(MODERN_CHART).strip();

    assertThat(markdown).isEqualTo("custom chart block (Gráfico)");
  }

  @Test
  void without_a_resolver_it_renders_an_image_with_the_media_placeholder() {
    var result = AdfToMarkdown.create().convert(INLINE_MEDIA_IMAGE);

    assertThat(result.body())
        .isEqualTo("logo ![media](media:contentId-326931860/7e62545e-7cdc-42a6-8760-137006ae963b)");
    assertThat(result.diagnostics()).noneMatch(d -> d.code() == Diagnostic.Code.UNSUPPORTED_MACRO);
  }

  @Test
  void a_media_resolver_turns_it_into_a_local_image_link() {
    var seen = new AdfMedia[1];
    var options =
        ConversionOptions.defaults()
            .withMediaResolver(
                media -> {
                  seen[0] = media;
                  return "images/logo.png";
                });

    var result = AdfToMarkdown.with(options).convert(INLINE_MEDIA_IMAGE);

    assertThat(result.body()).isEqualTo("logo ![media](images/logo.png)");
    assertThat(seen[0].id()).isEqualTo("7e62545e-7cdc-42a6-8760-137006ae963b");
    assertThat(seen[0].collection()).isEqualTo("contentId-326931860");
    assertThat(seen[0].sourceType()).isEqualTo("file");
    assertThat(seen[0].mediaType()).isEqualTo("image");
  }

  @Test
  void the_media_id_is_referenced_in_metadata_like_a_media_node() {
    var metadata = AdfToMarkdown.create().analyze(AdfJson.parse(INLINE_MEDIA_IMAGE)).metadata();

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
    assertThat(result.diagnostics()).anyMatch(d -> d.code() == Diagnostic.Code.UNSUPPORTED_MACRO);
  }

  // A pagetree extension; a null root omits the macroParams, otherwise it sets the "root" param.
  private static String pageTreeMacro(String root) {
    if (root == null) {
      return """
          {
            "type": "doc",
            "version": 1,
            "content": [
              {
                "type": "extension",
                "attrs": {
                  "extensionType": "com.atlassian.confluence.macro.core",
                  "extensionKey": "pagetree"
                }
              }
            ]
          }
          """;
    }
    return """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "extension",
              "attrs": {
                "extensionType": "com.atlassian.confluence.macro.core",
                "extensionKey": "pagetree",
                "parameters": { "macroParams": { "root": { "value": "%s" } } }
              }
            }
          ]
        }
        """
        .formatted(root);
  }
}
