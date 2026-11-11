package dev.nthings.adf4j.options;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import org.junit.jupiter.api.Test;

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
  void html_fallback_keeps_a_headerless_table_as_raw_html() {
    var options = ConversionOptions.defaults().withTableFallback(TableFallback.HTML);
    var markdown = AdfToMarkdown.with(options).toMarkdown(HEADERLESS_TABLE).strip();

    assertThat(markdown)
        .isEqualTo(
            "<table><tr><td>a1</td><td>a2</td></tr>" + "<tr><td>b1</td><td>b2</td></tr></table>");
  }

  @Test
  void default_keeps_every_original_row_as_data() {
    var options = ConversionOptions.defaults();
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
    var options =
        ConversionOptions.defaults().withTableFallback(TableFallback.GFM_PROMOTE_FIRST_ROW);
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
    var options =
        ConversionOptions.defaults().withTableFallback(TableFallback.GFM_PROMOTE_FIRST_ROW);
    var markdown = AdfToMarkdown.with(options).toMarkdown(COLSPAN_TABLE).strip();

    assertThat(markdown)
        .isEqualTo(
            "<table><tr><td colspan=\"2\">wide</td></tr><tr><td>b1</td><td>b2</td></tr></table>");
  }

  @Test
  void html_fallback_preserves_relative_and_http_links_in_a_cell() {
    // The fallback re-parses cell Markdown to HTML; its URL sanitiser must keep safe hrefs.
    var adf =
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

    assertThat(markdown)
        .isEqualTo(
            "<table><tr><td colspan=\"2\"><a rel=\"nofollow\" href=\"/wiki/page\">rel</a><a rel=\"nofollow\" href=\"https://example.com/p\"> abs</a></td></tr></table>");
  }

  @Test
  void a_captioned_image_in_an_html_cell_keeps_balanced_paragraphs() {
    var adf =
        """
        {"type": "doc", "version": 1, "content": [{"type": "table", "content": [{"type": "tableRow", "content": [
          {"type": "tableCell", "attrs": {"colspan": 2}, "content": [{"type": "mediaSingle", "content": [
            {"type": "media", "attrs": {"type": "external", "url": "https://a/1.png"}},
            {"type": "caption", "content": [{"type": "text", "text": "cap"}]}
          ]}]}
        ]}]}]}
        """;

    var html = AdfToMarkdown.create().toMarkdown(adf);

    assertThat(html)
        .isEqualTo(
            """
            <table><tr><td colspan="2"><p><img src="https://a/1.png" alt="media"></p>
            <p>cap</p></td></tr></table>""");
  }
}
