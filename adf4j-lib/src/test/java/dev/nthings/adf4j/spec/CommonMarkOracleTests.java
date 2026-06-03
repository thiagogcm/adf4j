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
  void indented_paragraph_stays_a_paragraph_and_does_not_promote_to_a_code_block() {
    // The nbsp indent run is plain text, so a deeply-indented paragraph must stay a <p>, never a
    // 4-space indented <pre><code> block.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph",
             "marks": [{"type": "indentation", "attrs": {"level": 2}}],
             "content": [{"type": "text", "text": "Indented twice."}]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("pre, code")).isEmpty();
    assertThat(document.select("p")).hasSize(1);
    assertThat(document.selectFirst("p").text()).endsWith("Indented twice.");
  }

  @Test
  void indented_heading_stays_a_heading() {
    // The nbsp run sits after the "# " marker, so the block is still parsed as an ATX heading.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "heading", "attrs": {"level": 2},
             "marks": [{"type": "indentation", "attrs": {"level": 1}}],
             "content": [{"type": "text", "text": "Indented heading"}]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("h2")).hasSize(1);
    assertThat(document.selectFirst("h2").text()).endsWith("Indented heading");
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
  void placeholder_with_link_and_emphasis_metacharacters_stays_inert_text() {
    // A placeholder carrying "[label](http://evil) *x*" must not parse into an anchor or emphasis.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [
              {"type": "text", "text": "Owner: "},
              {"type": "placeholder", "attrs": {"text": "[label](http://evil) *x*"}}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("a")).isEmpty();
    assertThat(document.select("em")).isEmpty();
    assertThat(document.selectFirst("p").text()).isEqualTo("Owner: [label](http://evil) *x*");
  }

  @Test
  void placeholder_as_first_inline_starting_with_hash_does_not_become_a_heading() {
    // A placeholder is a block's first inline; its "# ..." text must stay a paragraph.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [
              {"type": "placeholder", "attrs": {"text": "# fill in the title"}}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("h1, h2, h3, h4, h5, h6")).isEmpty();
    assertThat(document.select("p")).hasSize(1);
    assertThat(document.selectFirst("p").text()).isEqualTo("# fill in the title");
  }

  @Test
  void mention_as_first_inline_starting_with_dash_does_not_become_a_list() {
    // A mention's "- ..." text at line start must not promote to a bullet list item.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [
              {"type": "mention", "attrs": {"id": "user-7", "text": "- dash-led name"}}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("ul, ol, li")).isEmpty();
    assertThat(document.select("p")).hasSize(1);
    assertThat(document.selectFirst("p").text()).isEqualTo("- dash-led name");
  }

  @Test
  void non_numeric_date_as_first_inline_starting_with_gt_does_not_become_a_blockquote() {
    // A non-numeric date timestamp passes through verbatim; a leading ">" must not start a quote.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [
              {"type": "date", "attrs": {"timestamp": "> see linked page"}}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("blockquote")).isEmpty();
    assertThat(document.select("p")).hasSize(1);
    assertThat(document.selectFirst("p").text()).isEqualTo("> see linked page");
  }

  @Test
  void status_then_placeholder_with_leading_paren_does_not_inject_a_link() {
    // "[Done]" (status) glued to "(see ref)" (placeholder) must not parse into an inline link.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [
              {"type": "status", "attrs": {"text": "Done", "color": "green"}},
              {"type": "placeholder", "attrs": {"text": "(see ref)"}}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("a")).isEmpty();
    assertThat(document.selectFirst("p").text()).isEqualTo("[Done](see ref)");
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

  @Test
  void toc_referenced_heading_emits_an_anchor_that_matches_its_toc_link() {
    // A duplicate heading name slugs to "dup" / "dup-1"; the injected <a id> for each must equal the
    // toc link target so the link resolves on any consumer, not just a commonmark-compatible slugger.
    var adf = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "extension", "attrs": {"extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "toc"}},
            {"type": "heading", "attrs": {"level": 1}, "content": [{"type": "text", "text": "Dup"}]},
            {"type": "heading", "attrs": {"level": 1}, "content": [{"type": "text", "text": "Dup"}]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    var tocTargets = document.select("ul a").eachAttr("href");
    assertThat(tocTargets).containsExactly("#dup", "#dup-1");
    for (var target : tocTargets) {
      assertThat(document.select("a[id=" + target.substring(1) + "]"))
          .as("injected anchor for %s", target)
          .hasSize(1);
    }
  }
}
