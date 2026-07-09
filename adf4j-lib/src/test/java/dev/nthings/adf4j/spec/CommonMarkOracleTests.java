package dev.nthings.adf4j.spec;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.options.MarkdownOptions;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

/// Independent structural oracle: rather than re-comparing against a converter-produced snapshot,
/// each case parses the converter's Markdown with `commonmark` (production's extension
/// list), renders to HTML, and asserts structural invariants — catching output that looks plausible
/// but parses into the wrong tree (a stray heading, a list escaping a paragraph, a leaked HTML
/// tag).
class CommonMarkOracleTests {

  private static final AdfToMarkdown CONVERTER = AdfToMarkdown.create();
  private static final AdfToMarkdown VISUAL_CONVERTER =
      AdfToMarkdown.with(MarkdownOptions.defaults().withHtmlVisualMarks(true));

  private static String toHtml(String adfJson) {
    return CommonMarkTestSupport.toHtml(CONVERTER.toMarkdown(adfJson));
  }

  private static String toHtmlVisual(String adfJson) {
    return CommonMarkTestSupport.toHtml(VISUAL_CONVERTER.toMarkdown(adfJson));
  }

  private static String roundTripMarkdown(String adfJson) {
    return CommonMarkTestSupport.roundTripMarkdown(CONVERTER.toMarkdown(adfJson));
  }

  private static String roundTripToHtml(String adfJson) {
    return CommonMarkTestSupport.roundTripToHtml(CONVERTER.toMarkdown(adfJson));
  }

  @Test
  void level_two_heading_parses_to_an_h2_with_matching_text() {
    var adf =
        """
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
    var adf =
        """
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
    var adf =
        """
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
    var adf =
        """
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
    var adf =
        """
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
    var adf =
        """
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
    var adf =
        """
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
    var adf =
        """
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
    var adf =
        """
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
    var adf =
        """
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
    var adf =
        """
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
    var adf =
        """
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
    var adf =
        """
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
    var adf =
        """
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
    // A duplicate heading name slugs to "dup" / "dup-1"; the injected <a id> for each must equal
    // the
    // toc link target so the link resolves on any consumer, not just a commonmark-compatible
    // slugger.
    var adf =
        """
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

  @Test
  void header_column_table_keeps_the_header_cells_in_the_left_column_of_both_rows() {
    // A header COLUMN (first cell of each row is a tableHeader) must not be promoted to a GFM
    // header
    // row: the fallback routes to raw HTML, so the <th> stay in the left column of both rows.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "table", "content": [
              {"type": "tableRow", "content": [
                {"type": "tableHeader",
                 "content": [{"type": "paragraph", "content": [{"type": "text", "text": "R1H"}]}]},
                {"type": "tableCell",
                 "content": [{"type": "paragraph", "content": [{"type": "text", "text": "r1c2"}]}]}
              ]},
              {"type": "tableRow", "content": [
                {"type": "tableHeader",
                 "content": [{"type": "paragraph", "content": [{"type": "text", "text": "R2H"}]}]},
                {"type": "tableCell",
                 "content": [{"type": "paragraph", "content": [{"type": "text", "text": "r2c2"}]}]}
              ]}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("table")).hasSize(1);
    assertThat(document.select("th").eachText()).containsExactly("R1H", "R2H");
    for (var row : document.select("tr")) {
      assertThat(row.child(0).tagName()).as("first cell of each row is the header").isEqualTo("th");
      assertThat(row.child(1).tagName()).as("second cell of each row is data").isEqualTo("td");
    }
  }

  @Test
  void header_row_that_is_not_first_keeps_its_cells_as_th_outside_the_first_row() {
    // A header row in second position must stay <th> and must not move to the first row.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "table", "content": [
              {"type": "tableRow", "content": [
                {"type": "tableCell",
                 "content": [{"type": "paragraph", "content": [{"type": "text", "text": "d1"}]}]},
                {"type": "tableCell",
                 "content": [{"type": "paragraph", "content": [{"type": "text", "text": "d2"}]}]}
              ]},
              {"type": "tableRow", "content": [
                {"type": "tableHeader",
                 "content": [{"type": "paragraph", "content": [{"type": "text", "text": "H1"}]}]},
                {"type": "tableHeader",
                 "content": [{"type": "paragraph", "content": [{"type": "text", "text": "H2"}]}]}
              ]}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("table")).hasSize(1);
    assertThat(document.select("th").eachText()).containsExactly("H1", "H2");
    var firstRow = document.selectFirst("tr");
    assertThat(firstRow.select("th")).as("the header cells are not in the first row").isEmpty();
    assertThat(firstRow.select("td").eachText()).containsExactly("d1", "d2");
  }

  @Test
  void info_panel_parses_to_a_gfm_alert_container_keeping_its_body_text() {
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "panel", "attrs": {"panelType": "info"},
             "content": [{"type": "paragraph",
               "content": [{"type": "text", "text": "Heads up."}]}]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("div.markdown-alert")).as("the alert container exists").hasSize(1);
    assertThat(document.selectFirst("div.markdown-alert").text()).contains("Heads up.");
  }

  @Test
  void panel_with_nested_list_round_trips_through_commonmark_markdown_renderer() {
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "panel", "attrs": {"panelType": "info"},
             "content": [{"type": "bulletList", "content": [
               {"type": "listItem", "content": [
                 {"type": "paragraph", "content": [{"type": "text", "text": "Alpha"}]}
               ]},
               {"type": "listItem", "content": [
                 {"type": "paragraph", "content": [{"type": "text", "text": "Beta"}]}
               ]}
             ]}]}
          ]
        }
        """;

    var markdown = roundTripMarkdown(adf);
    var document = Jsoup.parse(roundTripToHtml(adf));

    assertThat(markdown).contains("> [!NOTE]").contains("> - Alpha").contains("> - Beta");
    assertThat(document.select("div.markdown-alert")).hasSize(1);
    assertThat(document.select("div.markdown-alert li").eachText())
        .containsExactly("Alpha", "Beta");
  }

  @Test
  void task_list_round_trips_through_commonmark_markdown_renderer() {
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "taskList", "attrs": {"localId": "list-1"}, "content": [
              {"type": "taskItem", "attrs": {"localId": "item-1", "state": "DONE"},
               "content": [{"type": "text", "text": "Parent"}]},
              {"type": "taskList", "attrs": {"localId": "list-2"}, "content": [
                {"type": "taskItem", "attrs": {"localId": "item-2", "state": "TODO"},
                 "content": [{"type": "text", "text": "Child"}]}
              ]}
            ]}
          ]
        }
        """;

    var markdown = roundTripMarkdown(adf);
    var document = Jsoup.parse(roundTripToHtml(adf));

    assertThat(markdown).contains("- [x] Parent").contains("  - [ ] Child");
    assertThat(document.select("li").eachText()).containsExactly("Parent Child", "Child");
    assertThat(document.select("input[type=checkbox]")).hasSize(2);
    assertThat(document.select("input[checked]")).hasSize(1);
  }

  @Test
  void html_table_fallback_cell_keeps_task_list_and_alert_after_round_trip() {
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "table", "content": [
              {"type": "tableRow", "content": [
                {"type": "tableCell", "attrs": {"colspan": 2}, "content": [
                  {"type": "taskList", "attrs": {"localId": "tasks"}, "content": [
                    {"type": "taskItem", "attrs": {"localId": "task-1", "state": "DONE"},
                     "content": [{"type": "text", "text": "Checked in table"}]},
                    {"type": "taskItem", "attrs": {"localId": "task-2", "state": "TODO"},
                     "content": [{"type": "text", "text": "Open in table"}]}
                  ]},
                  {"type": "panel", "attrs": {"panelType": "warning"},
                   "content": [{"type": "paragraph",
                     "content": [{"type": "text", "text": "Alert in table"}]}]}
                ]}
              ]}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(roundTripToHtml(adf));

    assertThat(document.select("table")).hasSize(1);
    assertThat(document.select("td[colspan=2]")).hasSize(1);
    assertThat(document.select("td li").eachText())
        .containsExactly("Checked in table", "Open in table");
    assertThat(document.select("td input[type=checkbox]")).hasSize(2);
    assertThat(document.select("td div.markdown-alert")).hasSize(1);
    assertThat(document.selectFirst("td div.markdown-alert").text()).contains("Alert in table");
  }

  @Test
  void expand_block_parses_to_a_details_summary_disclosure() {
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "expand", "attrs": {"title": "More details"},
             "content": [{"type": "paragraph",
               "content": [{"type": "text", "text": "Hidden body."}]}]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("details")).hasSize(1);
    assertThat(document.selectFirst("details > summary").text()).isEqualTo("More details");
    assertThat(document.selectFirst("details").text()).contains("Hidden body.");
  }

  @Test
  void nested_bullet_list_keeps_its_nesting() {
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "bulletList", "content": [
              {"type": "listItem", "content": [
                {"type": "paragraph", "content": [{"type": "text", "text": "Outer"}]},
                {"type": "bulletList", "content": [
                  {"type": "listItem", "content": [
                    {"type": "paragraph", "content": [{"type": "text", "text": "Inner"}]}
                  ]}
                ]}
              ]}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("ul ul li"))
        .as("the nested item survives as a nested li")
        .hasSize(1);
    assertThat(document.selectFirst("ul ul li").text()).isEqualTo("Inner");
  }

  @Test
  void bang_before_a_linked_text_node_does_not_form_an_image() {
    // "Heads up!" glued to a link "the runbook" must not let the "!"+"[" parse into an image.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [
              {"type": "text", "text": "Heads up!"},
              {"type": "text", "text": "the runbook",
               "marks": [{"type": "link", "attrs": {"href": "https://ex.com/r"}}]}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    assertThat(document.select("a")).as("the link survived as an anchor").hasSize(1);
    assertThat(document.selectFirst("a").attr("href")).isEqualTo("https://ex.com/r");
    assertThat(document.select("img")).as("the '!'+'[' did not form an image").isEmpty();
  }

  @Test
  void inline_card_label_with_metacharacters_stays_inert_link_text() {
    // An inlineCard whose data.name carries '*', backtick and '!' must escape into the link text
    // without spawning emphasis, code, or an image.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "paragraph", "content": [
              {"type": "text", "text": "See "},
              {"type": "inlineCard", "attrs": {"data": {"@type": "Object",
                 "name": "Use *bold* `now`!", "url": "https://example.com/x"}}}
            ]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtml(adf));

    var anchor = document.selectFirst("a[href=https://example.com/x]");
    assertThat(anchor).as("the card linkified").isNotNull();
    assertThat(anchor.text()).isEqualTo("Use *bold* `now`!");
    assertThat(document.select("em")).as("no stray emphasis").isEmpty();
    assertThat(document.select("code")).as("no stray code span").isEmpty();
    assertThat(document.select("img")).as("no stray image").isEmpty();
  }

  @Test
  void aligned_block_wraps_in_a_div_whose_markdown_still_renders() {
    // Under htmlVisualMarks an aligned heading + bold text must sit inside <div align> AND keep
    // being parsed as markdown — i.e. the div must not swallow it as a raw-HTML block.
    var adf =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "heading", "attrs": {"level": 2},
             "marks": [{"type": "alignment", "attrs": {"align": "center"}}],
             "content": [{"type": "text", "text": "Centered"}]},
            {"type": "paragraph",
             "marks": [{"type": "alignment", "attrs": {"align": "end"}}],
             "content": [{"type": "text", "text": "right "},
                         {"type": "text", "text": "bold", "marks": [{"type": "strong"}]}]}
          ]
        }
        """;

    var document = Jsoup.parse(toHtmlVisual(adf));

    var centered = document.selectFirst("div[align=center]");
    assertThat(centered).as("centered heading is wrapped").isNotNull();
    assertThat(centered.selectFirst("h2")).as("heading still parses as h2").isNotNull();
    assertThat(centered.selectFirst("h2").text()).isEqualTo("Centered");

    var right = document.selectFirst("div[align=right]");
    assertThat(right).as("end-aligned paragraph is wrapped").isNotNull();
    assertThat(right.selectFirst("strong")).as("bold still parses").isNotNull();
    assertThat(right.selectFirst("strong").text()).isEqualTo("bold");
  }
}
