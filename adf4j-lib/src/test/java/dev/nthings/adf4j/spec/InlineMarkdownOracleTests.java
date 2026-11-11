package dev.nthings.adf4j.spec;

import static dev.nthings.adf4j.testing.AdfSnippets.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.TableFallback;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/// What inline nodes render to, and what CommonMark actually parses from it: text-run merging and
/// the escaping that depends on where a node lands in its line.
class InlineMarkdownOracleTests {

  private static final String STRONG = mark("strong");
  private static final String EM = mark("em");
  private static final String CODE = mark("code");

  private static final AdfToMarkdown CONVERTER = AdfToMarkdown.create();
  private static final AdfToMarkdown VISUAL =
      AdfToMarkdown.with(ConversionOptions.defaults().withHtmlVisualMarks(true));

  private static String render(String... inlines) {
    return CONVERTER.toMarkdown(doc(paragraph(inlines)));
  }

  private static Document parsed(String markdown) {
    return Jsoup.parse(CommonMarkTestSupport.toHtml(markdown));
  }

  private static String color(String value) {
    return mark("textColor", "{\"color\":" + quote(value) + "}");
  }

  private static String subsup(String type) {
    return mark("subsup", "{\"type\":" + quote(type) + "}");
  }

  private static String linkWith(String attrs) {
    return mark("link", attrs);
  }

  private static String annotation(String id) {
    return mark("annotation", "{\"id\":" + quote(id) + ",\"annotationType\":\"inlineComment\"}");
  }

  private static List<List<String>> markLists() {
    var pool =
        List.of(
            STRONG,
            EM,
            CODE,
            mark("strike"),
            mark("underline"),
            subsup("sup"),
            subsup("SUP"),
            subsup("sub"),
            subsup("x"),
            color("#ff0000"),
            color("#00ff00"),
            color("red;top:0"),
            mark("backgroundColor", "{\"color\":\"#eeeeee\"}"),
            mark("fontSize", "{\"fontSize\":\"small\"}"),
            mark("border", "{\"size\":2,\"color\":\"#000000\"}"),
            link("https://a"),
            link("https://b"),
            linkWith("{\"href\":\"https://a\",\"id\":\"1\"}"),
            linkWith("{\"href\":\"https://a\",\"title\":\"t\"}"),
            linkWith("{\"href\":\" \"}"),
            annotation("a"),
            mark("alignment", "{\"align\":\"center\"}"),
            mark("indentation", "{\"level\":1}"),
            mark("breakout", "{\"mode\":\"wide\"}"),
            mark("futureMark", "{\"k\":1}"));
    var lists = new ArrayList<List<String>>();
    lists.add(List.of());
    for (var first : pool) {
      lists.add(List.of(first));
      for (var second : pool) {
        lists.add(List.of(first, second));
      }
    }
    return lists;
  }

  private static String[] marks(List<String> list) {
    return list.toArray(String[]::new);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void runs_merge_exactly_when_their_marks_render_identically(boolean visual) {
    var converter = visual ? VISUAL : CONVERTER;
    var lists = markLists();
    var rendered = new ArrayList<String>();
    for (var list : lists) {
      rendered.add(converter.toMarkdown(doc(paragraph(text("x", marks(list))))));
    }

    // Each list against a spread of others: all pairs would take minutes for no extra coverage.
    for (var left = 0; left < lists.size(); left++) {
      for (var right = left % 13; right < lists.size(); right += 13) {
        var merged = converter.toMarkdown(doc(paragraph(text("ab", marks(lists.get(left))))));
        var adjacent =
            converter.toMarkdown(
                doc(
                    paragraph(
                        text("a", marks(lists.get(left))), text("b", marks(lists.get(right))))));

        assertThat(adjacent.equals(merged))
            .as("%s + %s", lists.get(left), lists.get(right))
            .isEqualTo(rendered.get(left).equals(rendered.get(right)));
      }
    }
  }

  @Test
  void a_non_rendering_extra_mark_does_not_split_a_bold_run() {
    var markdown = render(text("foo", STRONG), text("bar", STRONG, annotation("a")));

    assertThat(markdown).isEqualTo("**foobar**");
    assertThat(parsed(markdown).select("strong").eachText()).containsExactly("foobar");
  }

  @Test
  void runs_merge_regardless_of_mark_order() {
    assertThat(render(text("a", STRONG, EM), text("b", EM, STRONG))).isEqualTo("***ab***");
  }

  @Test
  void repeated_marks_render_once_so_equal_effect_sets_really_render_alike() {
    assertThat(render(text("x", STRONG, STRONG))).isEqualTo("**x**");
    assertThat(render(text("a", STRONG, STRONG, EM), text("b", STRONG, EM, EM)))
        .isEqualTo("***ab***");
  }

  @Test
  void the_effect_comparison_keeps_differently_marked_runs_apart() {
    assertThat(render(text("a", STRONG), text("b", STRONG, EM))).isEqualTo("**a*****b***");
  }

  @Test
  void colors_split_a_run_only_when_they_render() {
    var red = text("a", color("#ff0000"));
    var green = text("b", color("#00ff00"));

    assertThat(CONVERTER.toMarkdown(doc(paragraph(red, green)))).isEqualTo("ab");
    assertThat(VISUAL.toMarkdown(doc(paragraph(red, green))))
        .isEqualTo("<span style=\"color:#ff0000\">a</span><span style=\"color:#00ff00\">b</span>");
  }

  @Test
  void a_color_that_does_not_render_merges_with_plain_text_under_visual_marks() {
    assertThat(VISUAL.toMarkdown(doc(paragraph(text("a", color("red;top:0")), text("b")))))
        .isEqualTo("ab");
  }

  @Test
  void the_last_of_two_same_property_declarations_wins_whatever_the_comparison_saw() {
    var ab = VISUAL.toMarkdown(doc(paragraph(text("x", color("#111111"), color("#222222")))));

    assertThat(ab).isEqualTo("<span style=\"color:#222222\">x</span>");
  }

  @Test
  void subsup_spellings_that_render_alike_merge() {
    assertThat(render(text("a", subsup("sup")), text("b", subsup("SUP"))))
        .isEqualTo("<sup>ab</sup>");
  }

  @Test
  void unknown_subsup_types_render_nothing_so_they_merge_with_each_other_and_with_plain_text() {
    assertThat(render(text("a", subsup("x")), text("b", subsup("y")), text("c"))).isEqualTo("abc");
  }

  @Test
  void link_attrs_that_do_not_render_do_not_split_a_run() {
    var markdown =
        render(
            text("a", linkWith("{\"href\":\"https://e.com\",\"id\":\"1\",\"title\":\" \"}")),
            text("b", linkWith("{\"href\":\"https://e.com\",\"occurrenceKey\":\"k\"}")));

    assertThat(markdown).isEqualTo("[ab](https://e.com)");
  }

  @Test
  void a_link_without_an_href_renders_nothing_so_it_merges_with_plain_text() {
    assertThat(render(text("a", linkWith("{\"href\":\" \"}")), text("b"))).isEqualTo("ab");
  }

  @Test
  void links_with_different_destinations_or_titles_stay_apart() {
    assertThat(render(text("a", link("https://a")), text("b", link("https://b"))))
        .isEqualTo("[a](https://a)[b](https://b)");
    assertThat(
            render(
                text("a", linkWith("{\"href\":\"https://a\",\"title\":\"t\"}")),
                text("b", link("https://a"))))
        .isEqualTo("[a](https://a \"t\")[b](https://a)");
  }

  @Test
  void page_metadata_splits_a_link_run_only_under_a_page_link_resolver() {
    var one = "{\"href\":\"/x\",\"__confluenceMetadata\":{\"pageId\":\"1\"}}";
    var two = "{\"href\":\"/x\",\"__confluenceMetadata\":{\"pageId\":\"2\"}}";
    var document = doc(paragraph(text("a", linkWith(one)), text("b", linkWith(two))));
    var calls = new ArrayList<String>();
    var resolving =
        AdfToMarkdown.with(
            ConversionOptions.defaults()
                .withPageLinkResolver(
                    id -> {
                      calls.add(id);
                      return "https://page/" + id;
                    }));

    assertThat(CONVERTER.toMarkdown(document)).isEqualTo("[ab](/x)");
    assertThat(resolving.toMarkdown(document)).isEqualTo("[a](https://page/1)[b](https://page/2)");
    assertThat(calls).containsExactly("1", "2");
  }

  @Test
  void merged_link_runs_look_their_page_up_once() {
    var calls = new ArrayList<String>();
    var resolving =
        AdfToMarkdown.with(
            ConversionOptions.defaults()
                .withPageLinkResolver(
                    id -> {
                      calls.add(id);
                      return "https://page/" + id;
                    }));
    var page = "https://x.atlassian.net/wiki/spaces/S/pages/123/T";

    var markdown =
        resolving.toMarkdown(doc(paragraph(text("a", link(page)), text("b", link(page), STRONG))));
    assertThat(markdown).isEqualTo("[a](https://page/123)[**b**](https://page/123)");
    calls.clear();

    assertThat(resolving.toMarkdown(doc(paragraph(text("a", link(page)), text("b", link(page))))))
        .isEqualTo("[ab](https://page/123)");
    assertThat(calls).containsExactly("123");
  }

  @Test
  void unknown_marks_merge_but_each_one_still_counts_as_dropped() {
    var result =
        CONVERTER.convert(
            doc(
                paragraph(
                    text("a", mark("futureMark", "{\"k\":1}")),
                    text("b", mark("futureMark", "{\"k\":2}")))));

    assertThat(result.body()).isEqualTo("ab");
    assertThat(result.diagnostics().getFirst().message()).startsWith("2 unsupported mark(s)");
  }

  @Test
  void code_supersedes_formatting_so_code_runs_merge_into_one_span() {
    var markdown = render(text("a_", CODE), text("b", CODE, STRONG));

    assertThat(markdown).isEqualTo("`a_b`");
    assertThat(parsed(markdown).select("code").eachText()).containsExactly("a_b");
  }

  @ParameterizedTest
  @ValueSource(strings = {"one\ntwo", "one\r\ntwo", "one\u2028two"})
  void a_line_break_in_heading_text_keeps_the_heading_on_one_line(String value) {
    var markdown = CONVERTER.toMarkdown(doc(heading(2, text(value))));

    assertThat(markdown).isEqualTo("## one two");
    assertThat(parsed(markdown).select("h2").eachText()).containsExactly("one two");
    assertThat(parsed(markdown).select("p")).isEmpty();
  }

  @Test
  void every_inline_kind_stays_on_the_heading_line() {
    var markdown =
        CONVERTER.toMarkdown(
            doc(
                heading(
                    1,
                    text("t\nu", CODE),
                    text(" "),
                    atom("placeholder", "{\"text\":\"p\\nq\"}"),
                    text(" "),
                    atom("mention", "{\"text\":\"m\\nn\"}"))));

    assertThat(markdown).isEqualTo("# `t u` p q m n");
    assertThat(parsed(markdown).select("h1")).hasSize(1);
    assertThat(parsed(markdown).select("p")).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"- dash|\\- dash", "# hash|\\# hash", "1. one|1\\. one", "> q|\\> q"})
  void a_whitespace_only_leading_inline_keeps_the_next_one_at_line_start(
      String value, String expected) {
    var markdown = render(text(" ", EM), text(value));

    assertThat(markdown).isEqualTo(" " + expected);
    assertThat(parsed(markdown).select("ul, ol, h1, blockquote")).isEmpty();
    assertThat(parsed(markdown).select("p")).hasSize(1);
  }

  @Test
  void line_start_tracking_also_runs_after_a_hard_break_and_past_more_whitespace() {
    var markdown =
        render(text("a"), atom("hardBreak", "{}"), text(" ", EM), text(" ", STRONG), text("- b"));

    assertThat(markdown).isEqualTo("a  \n  \\- b");
    assertThat(parsed(markdown).select("ul")).isEmpty();
  }

  @Test
  void a_run_ending_in_a_line_break_puts_the_next_one_at_line_start() {
    var markdown = render(text("a\n", EM), text("- b"));

    assertThat(markdown).isEqualTo("*a*\n\\- b");
    assertThat(parsed(markdown).select("ul")).isEmpty();
  }

  @Test
  void text_after_real_content_is_not_at_line_start() {
    assertThat(render(text("a", EM), text(" - b"))).isEqualTo("*a* - b");
  }

  private static String tableOf(String... paragraphs) {
    var cells = new ArrayList<String>();
    for (var paragraph : paragraphs) {
      cells.add(node("tableHeader", "{}", paragraph));
    }
    return doc(node("table", "{}", node("tableRow", "{}", cells.toArray(String[]::new))));
  }

  @Test
  void no_inline_kind_is_neutralized_in_a_gfm_cell() {
    var markdown =
        CONVERTER
            .toMarkdown(
                tableOf(
                    paragraph(text("# t")),
                    paragraph(atom("placeholder", "{\"text\":\"# p\"}")),
                    paragraph(atom("mention", "{\"text\":\"- m\"}")),
                    paragraph(atom("emoji", "{\"text\":\"1. e\"}")),
                    paragraph(
                        text("a"),
                        atom("hardBreak", "{}"),
                        atom("placeholder", "{\"text\":\"> b\"}"))))
            .lines()
            .findFirst()
            .orElseThrow();

    assertThat(markdown)
        .contains("| # t ")
        .contains("| # p ")
        .contains("| - m ")
        .contains("| 1. e ")
        .contains("| a<br>> b ")
        .doesNotContain("\\");
    assertThat(
            parsed(
                    CONVERTER.toMarkdown(
                        tableOf(
                            paragraph(text("# t")),
                            paragraph(atom("placeholder", "{\"text\":\"# p\"}")))))
                .select("th")
                .eachText())
        .containsExactly("# t", "# p");
  }

  @Test
  void literal_markers_in_a_gfm_cell_are_not_taken_for_lists_to_separate() {
    var cell = node("tableHeader", "{}", paragraph(text("- a")), paragraph(text("- b")));

    assertThat(CONVERTER.toMarkdown(doc(node("table", "{}", node("tableRow", "{}", cell)))))
        .startsWith("| - a<br><br>- b |");
  }

  @Test
  void an_html_cell_still_neutralizes_markers_because_it_is_reparsed_as_a_block_document() {
    var html =
        AdfToMarkdown.with(ConversionOptions.defaults().withTableFallback(TableFallback.HTML))
            .toMarkdown(
                doc(
                    node(
                        "table",
                        "{}",
                        node(
                            "tableRow",
                            "{}",
                            node(
                                "tableCell",
                                "{}",
                                paragraph(atom("placeholder", "{\"text\":\"# p\"}")))))));

    assertThat(Jsoup.parse(html).select("td h1")).isEmpty();
    assertThat(Jsoup.parse(html).select("td").text()).isEqualTo("# p");
  }

  private static String inlineExtension(String text) {
    return atom(
        "inlineExtension",
        "{\"extensionType\":\"x\",\"extensionKey\":\"y\",\"text\":" + quote(text) + "}");
  }

  @Test
  void inline_extension_fallback_text_is_escaped_for_its_real_position() {
    assertThat(render(text("x "), inlineExtension("- mid"))).isEqualTo("x - mid");
    assertThat(render(inlineExtension("- start"))).isEqualTo("\\- start");
    assertThat(render(text(" ", EM), inlineExtension("- ws"))).isEqualTo(" \\- ws");
    assertThat(CONVERTER.toMarkdown(doc(heading(1, inlineExtension("- h"))))).isEqualTo("# - h");
    assertThat(CONVERTER.toMarkdown(tableOf(paragraph(inlineExtension("- c")))).lines().findFirst())
        .contains("| - c |");
  }

  @Test
  void block_extension_fallback_text_stays_escaped_at_column_zero() {
    var extension =
        atom("extension", "{\"extensionType\":\"x\",\"extensionKey\":\"y\",\"text\":\"- block\"}");

    assertThat(CONVERTER.toMarkdown(doc(extension))).isEqualTo("\\- block");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"title| a\\nb", "title|   a\\r\\nb  "})
  void the_chart_default_label_collapses_line_breaks(String param, String raw) {
    var title = raw.replace("\\n", "\n").replace("\\r", "\r");
    var markdown =
        CONVERTER.toMarkdown(
            doc(
                macro(
                    "extension",
                    "chart:default",
                    "{" + quote(param) + ":{\"value\":" + quote(title) + "}}")));

    assertThat(markdown).isEqualTo("\\[Chart: a b\\]");
  }

  private static String image(String alt) {
    return doc(
        node(
            "mediaSingle",
            "{}",
            atom(
                "media",
                "{\"type\":\"external\",\"url\":\"https://e.com/a.png\",\"alt\":"
                    + quote(alt)
                    + "}")));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"C:\\ | C:\\\\", "a\\]b | a\\\\\\]b", "[x] | \\[x\\]"})
  void image_alt_text_survives_commonmark(String alt, String escaped) {
    var markdown = CONVERTER.toMarkdown(image(alt));

    assertThat(markdown).isEqualTo("![" + escaped + "](https://e.com/a.png)");
    var images = parsed(markdown).select("img");
    assertThat(images).hasSize(1);
    assertThat(images.getFirst().attr("alt")).isEqualTo(alt);
  }
}
