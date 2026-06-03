package dev.nthings.adf4j.spec;

import java.util.List;

import dev.nthings.adf4j.AdfToMarkdown;

import org.commonmark.ext.gfm.alerts.AlertsExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.image.attributes.ImageAttributesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Independent structural oracle: rather than re-comparing against a converter-produced snapshot,
 * each case parses the converter's Markdown with {@code org.commonmark} (production's extension
 * list), renders to HTML, and asserts structural invariants — catching output that looks plausible
 * but parses into the wrong tree (a stray heading, a list escaping a paragraph, a leaked HTML tag).
 */
class CommonMarkOracleTests {

  private static final AdfToMarkdown CONVERTER = AdfToMarkdown.create();

  // Same extension list as AdfRenderer.commonmarkExtensions().
  private static final List<org.commonmark.Extension> EXTENSIONS = List.of(
      TablesExtension.create(),
      StrikethroughExtension.create(),
      TaskListItemsExtension.create(),
      HeadingAnchorExtension.create(),
      ImageAttributesExtension.create(),
      AlertsExtension.create());

  private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
  private static final HtmlRenderer HTML_RENDERER =
      HtmlRenderer.builder().extensions(EXTENSIONS).sanitizeUrls(false).build();

  private static String toHtml(String adfJson) {
    var markdown = CONVERTER.toMarkdown(adfJson);
    return HTML_RENDERER.render(PARSER.parse(markdown));
  }

  @Test
  void level_two_heading_parses_to_an_h2_with_matching_text() {
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "heading", "attrs": {"level": 2},
             "content": [{"type": "text", "text": "Release Notes"}]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("h2")).hasSize(1);
    assertThat(document.selectFirst("h2").text()).isEqualTo("Release Notes");
    assertThat(document.select("h1")).isEmpty();
  }

  @Test
  void paragraph_that_merely_starts_with_text_does_not_produce_a_stray_heading() {
    // "# notes" inside a paragraph's text must render as a paragraph, never an ATX heading.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph",
             "content": [{"type": "text", "text": "# notes are not a heading"}]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("h1, h2, h3, h4, h5, h6")).isEmpty();
    assertThat(document.select("p")).hasSize(1);
    assertThat(document.selectFirst("p").text()).isEqualTo("# notes are not a heading");
  }

  @Test
  void expand_title_with_inline_html_does_not_introduce_a_live_b_element() {
    // The title "A <b>B</b> & C" must be HTML-escaped, so no live <b> appears in the summary.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "expand", "attrs": {"title": "A <b>B</b> & C"},
             "content": [{"type": "paragraph",
               "content": [{"type": "text", "text": "Body."}]}]}
          ]
        }
        """;

    var html = toHtml(adf);
    var document = Jsoup.parse(html);

    assertThat(document.select("summary b")).isEmpty();
    assertThat(html).contains("&lt;b&gt;").doesNotContain("<b>");
    assertThat(document.selectFirst("summary").text()).isEqualTo("A <b>B</b> & C");
  }

  @Test
  void single_text_node_with_embedded_markers_stays_one_paragraph() {
    // A single text node "intro\n# heading\n- item" stays one paragraph, no heading/list.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph",
             "content": [{"type": "text", "text": "intro\\n# heading\\n- item"}]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("p")).hasSize(1);
    assertThat(document.select("h1")).isEmpty();
    assertThat(document.select("ul")).isEmpty();
    assertThat(document.select("li")).isEmpty();
    var paragraphText = document.selectFirst("p").text();
    assertThat(paragraphText).contains("intro").contains("# heading").contains("- item");
  }

  @Test
  void inline_card_from_json_ld_data_keeps_both_url_and_name() {
    // An inlineCard carrying only JSON-LD data{name,url} must linkify with the name as the text.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [
              {"type": "text", "text": "See "},
              {"type": "inlineCard", "attrs": {"data": {"@type": "Object",
                 "name": "Ticket 9", "url": "https://example.com/ticket/9"}}}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    var anchor = document.selectFirst("a[href=https://example.com/ticket/9]");
    assertThat(anchor).as("the data.url survived into an <a href>").isNotNull();
    assertThat(anchor.text()).as("the data.name survived as the link text").isEqualTo("Ticket 9");
  }

  @Test
  void link_on_whitespace_with_bracket_in_href_stays_a_real_anchor() {
    // A link mark on whitespace-only text whose href contains ']' must remain a parseable anchor.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [
              {"type": "text", "text": " ",
               "marks": [{"type": "link", "attrs": {"href": "https://e.com/x]y"}}]}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    var anchor = document.selectFirst("a");
    assertThat(anchor).as("the link was not broken into literal text").isNotNull();
    assertThat(anchor.attr("href")).isEqualTo("https://e.com/x]y");
  }

  @Test
  void gfm_table_with_header_row_parses_to_a_table_with_a_header_cell() {
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "table", "content": [
              {"type": "tableRow", "content": [
                {"type": "tableHeader",
                 "content": [{"type": "paragraph", "content": [{"type": "text", "text": "Name"}]}]},
                {"type": "tableHeader",
                 "content": [{"type": "paragraph", "content": [{"type": "text", "text": "Score"}]}]}
              ]},
              {"type": "tableRow", "content": [
                {"type": "tableCell",
                 "content": [{"type": "paragraph", "content": [{"type": "text", "text": "Ada"}]}]},
                {"type": "tableCell",
                 "content": [{"type": "paragraph", "content": [{"type": "text", "text": "100"}]}]}
              ]}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("table")).hasSize(1);
    assertThat(document.select("th")).hasSize(2);
    assertThat(document.select("th").eachText()).containsExactly("Name", "Score");
    assertThat(document.select("td")).hasSize(2);
  }
}
