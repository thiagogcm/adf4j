package dev.nthings.adf4j.options;

import dev.nthings.adf4j.AdfToMarkdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TableFallbackOptionsTests {

  // A header-less 2x2 table: two tableCell-only rows (a1/a2 then b1/b2).
  private static final String HEADERLESS_TABLE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "table",
            "content": [
              {
                "type": "tableRow",
                "content": [
                  { "type": "tableCell", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "a1" } ] } ] },
                  { "type": "tableCell", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "a2" } ] } ] }
                ]
              },
              {
                "type": "tableRow",
                "content": [
                  { "type": "tableCell", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "b1" } ] } ] },
                  { "type": "tableCell", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "b2" } ] } ] }
                ]
              }
            ]
          }
        ]
      }
      """;

  // A table that GFM cannot express (a cell spanning two columns) stays HTML regardless of policy.
  private static final String COLSPAN_TABLE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "table",
            "content": [
              {
                "type": "tableRow",
                "content": [
                  { "type": "tableCell", "attrs": { "colspan": 2 }, "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "wide" } ] } ] }
                ]
              },
              {
                "type": "tableRow",
                "content": [
                  { "type": "tableCell", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "b1" } ] } ] },
                  { "type": "tableCell", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "b2" } ] } ] }
                ]
              }
            ]
          }
        ]
      }
      """;

  @Test
  void default_promotes_the_first_row_of_a_headerless_table() {
    var markdown = AdfToMarkdown.create().toMarkdown(HEADERLESS_TABLE).strip();

    assertThat(markdown)
        .isEqualTo(
            """
            | a1  | a2  |
            | --- | --- |
            | b1  | b2  |""");
  }

  @Test
  void html_fallback_keeps_a_headerless_table_as_raw_html() {
    var options = MarkdownOptions.defaults().withTableFallback(TableFallback.HTML);
    var markdown = AdfToMarkdown.with(options).toMarkdown(HEADERLESS_TABLE).strip();

    assertThat(markdown)
        .isEqualTo(
            "<table><tr><td>a1</td><td>a2</td></tr>"
                + "<tr><td>b1</td><td>b2</td></tr></table>");
  }

  @Test
  void gfm_empty_header_prepends_a_synthesized_empty_header_row() {
    var options = MarkdownOptions.defaults().withTableFallback(TableFallback.GFM_EMPTY_HEADER);
    var markdown = AdfToMarkdown.with(options).toMarkdown(HEADERLESS_TABLE).strip();

    assertThat(markdown)
        .isEqualTo(
            """
            |     |     |
            | --- | --- |
            | a1  | a2  |
            | b1  | b2  |""");
  }

  @Test
  void gfm_promote_first_row_treats_the_first_row_as_the_header() {
    var options = MarkdownOptions.defaults().withTableFallback(TableFallback.GFM_PROMOTE_FIRST_ROW);
    var markdown = AdfToMarkdown.with(options).toMarkdown(HEADERLESS_TABLE).strip();

    assertThat(markdown)
        .isEqualTo(
            """
            | a1  | a2  |
            | --- | --- |
            | b1  | b2  |""");
  }

  @Test
  void inexpressible_table_stays_html_even_under_a_gfm_policy() {
    var options = MarkdownOptions.defaults().withTableFallback(TableFallback.GFM_PROMOTE_FIRST_ROW);
    var markdown = AdfToMarkdown.with(options).toMarkdown(COLSPAN_TABLE).strip();

    assertThat(markdown).startsWith("<table>").contains("colspan=\"2\"").endsWith("</table>");
  }

  @Test
  void html_fallback_preserves_relative_and_http_links_in_a_cell() {
    // The fallback re-parses cell Markdown to HTML; its URL sanitiser must keep safe/relative hrefs.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "table",
              "content": [
                {
                  "type": "tableRow",
                  "content": [
                    {
                      "type": "tableCell",
                      "attrs": { "colspan": 2 },
                      "content": [
                        {
                          "type": "paragraph",
                          "content": [
                            {
                              "type": "text",
                              "text": "rel",
                              "marks": [{ "type": "link", "attrs": { "href": "/wiki/page" } }]
                            },
                            {
                              "type": "text",
                              "text": " abs",
                              "marks": [{ "type": "link", "attrs": { "href": "https://example.com/p" } }]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;

    var markdown = AdfToMarkdown.create().toMarkdown(adf).strip();

    assertThat(markdown).contains("href=\"/wiki/page\"").contains("href=\"https://example.com/p\"");
  }
}
