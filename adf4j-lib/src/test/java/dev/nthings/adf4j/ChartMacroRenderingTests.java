package dev.nthings.adf4j;

import java.util.List;

import dev.nthings.adf4j.options.MarkdownOptions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChartMacroRenderingTests {

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

  // The modern chart app shape observed in the wild: a bodyless extension whose data is a table
  // elsewhere in the document, referenced by a dataConsumer source.
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

  @Test
  void a_bodied_chart_renders_an_italic_caption_and_its_data_table() {
    var result = AdfToMarkdown.create().convert(LEGACY_BODIED_CHART);

    assertThat(result.body()).isEqualTo(
        """
        *Chart: Monthly visits*

        | Month   | Visits |
        | ------- | ------ |
        | January | 42     |""");
    assertThat(result.diagnostics()).noneMatch(d -> "UNSUPPORTED_MACRO".equals(d.code()));
  }

  @Test
  void a_modern_chart_renders_an_italic_caption_from_its_nested_title() {
    var result = AdfToMarkdown.create().convert(MODERN_CHART);

    // The chart's table is a separate document node (already rendered where it sits), so the chart
    // itself contributes only the caption — never a placeholder hole.
    assertThat(result.body()).isEqualTo("*Chart: Resoluções por mês*");
    assertThat(result.diagnostics()).noneMatch(d -> "UNSUPPORTED_MACRO".equals(d.code()));
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
    var options = MarkdownOptions.defaults().withExtensionRenderers(List.of(extension ->
        "com.atlassian.chart".equals(extension.extensionType())
            ? "custom chart block (" + extension.rawParameters().string("extensionTitle") + ")"
            : null));

    var markdown = AdfToMarkdown.with(options).toMarkdown(MODERN_CHART).strip();

    assertThat(markdown).isEqualTo("custom chart block (Gráfico)");
  }
}
