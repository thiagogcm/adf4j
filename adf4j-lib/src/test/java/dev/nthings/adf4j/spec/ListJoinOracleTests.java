package dev.nthings.adf4j.spec;

import static dev.nthings.adf4j.testing.AdfSnippets.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.testing.AdfSnippets;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/// Block marks on a list item's first paragraph, and the separator between adjacent lists: it
/// depends on what rendered at the join, not on which AST nodes are siblings.
class ListJoinOracleTests {

  private static final String SEPARATOR = "<!-- -->";
  private static final String CENTER = mark("alignment", "{\"align\":\"center\"}");
  private static final String INDENT = mark("indentation", "{\"level\":1}");
  private static final String NBSP_RUN = "\u00a0\u00a0\u00a0\u00a0";

  private static final AdfToMarkdown CONVERTER = AdfToMarkdown.create();
  private static final AdfToMarkdown VISUAL =
      AdfToMarkdown.with(ConversionOptions.defaults().withHtmlVisualMarks(true));

  private static Document parsed(String markdown) {
    return Jsoup.parse(CommonMarkTestSupport.toHtml(markdown));
  }

  private static String item(String... blocks) {
    return node("listItem", "{}", blocks);
  }

  private static String bulletList(String... items) {
    return node("bulletList", "{}", items);
  }

  private static String orderedList(int order, String... items) {
    return node("orderedList", "{\"order\":" + order + "}", items);
  }

  private static String numbers(String... values) {
    return orderedList(
        1,
        List.of(values).stream().map(value -> item(paragraph(text(value)))).toArray(String[]::new));
  }

  private static String excerpt(String... blocks) {
    return AdfSnippets.macro("bodiedExtension", "excerpt", "{}", blocks);
  }

  private static String columns(String... columnContents) {
    var columns =
        List.of(columnContents).stream()
            .map(content -> node("layoutColumn", "{\"width\":50}", content))
            .toArray(String[]::new);
    return node("layoutSection", "{}", columns);
  }

  @Test
  void the_first_paragraph_of_an_item_keeps_its_alignment_like_the_second() {
    var first = markedParagraph(CENTER, text("one"));
    var second = markedParagraph(CENTER, text("two"));

    var markdown = VISUAL.toMarkdown(doc(bulletList(item(first, second))));

    assertThat(markdown)
        .isEqualTo(
            """
            - <div align="center">

              one

              </div>

              <div align="center">

              two

              </div>""");
    assertThat(parsed(markdown).select("li p").eachText()).containsExactly("one", "two");
  }

  @Test
  void the_first_paragraph_of_an_item_keeps_its_indentation() {
    var markdown =
        CONVERTER.toMarkdown(
            doc(
                bulletList(
                    item(
                        markedParagraph(INDENT, text("one")),
                        markedParagraph(INDENT, text("two"))))));

    assertThat(markdown).isEqualTo("- " + NBSP_RUN + "one\n\n  " + NBSP_RUN + "two");
  }

  @Test
  void the_first_paragraph_of_a_task_item_keeps_its_block_marks_after_the_checkbox() {
    var task =
        node(
            "taskList",
            "{}",
            node("blockTaskItem", "{\"state\":\"TODO\"}", markedParagraph(INDENT, text("- one"))));

    assertThat(CONVERTER.toMarkdown(doc(task))).isEqualTo("- [ ] " + NBSP_RUN + "- one");
  }

  @Test
  void a_list_item_first_paragraph_still_neutralizes_leading_markers() {
    assertThat(CONVERTER.toMarkdown(doc(bulletList(item(paragraph(text("# no")))))))
        .isEqualTo("- \\# no");
  }

  @Test
  void no_separator_follows_an_empty_first_list() {
    var markdown = CONVERTER.toMarkdown(doc(bulletList(item(bulletList(), bullets("a")))));

    assertThat(markdown).isEqualTo("-\n  - a");
    assertThat(parsed(markdown).select("ul ul li").eachText()).containsExactly("a");
  }

  @Test
  void a_separator_still_follows_a_first_list_that_rendered() {
    var markdown = CONVERTER.toMarkdown(doc(bulletList(item(bullets("a"), bullets("b")))));

    assertThat(markdown).isEqualTo("-\n  - a\n\n  " + SEPARATOR + "\n\n  - b");
    assertThat(parsed(markdown).select("li > ul")).hasSize(2);
  }

  @Test
  void a_paragraph_after_an_empty_first_block_stays_in_its_item() {
    var markdown =
        CONVERTER.toMarkdown(doc(bulletList(item(paragraph(), paragraph(text("kept"))))));

    assertThat(parsed(markdown).select("li").eachText()).containsExactly("kept");
    assertThat(parsed(markdown).select("body > p")).isEmpty();
  }

  @Test
  void lists_across_layout_columns_stay_separate() {
    var markdown = CONVERTER.toMarkdown(doc(columns(bullets("a"), bullets("b"))));

    assertThat(markdown).isEqualTo("- a\n\n" + SEPARATOR + "\n\n- b");
    assertThat(parsed(markdown).select("ul")).hasSize(2);
  }

  @Test
  void ordered_lists_across_layout_columns_stay_separate() {
    var markdown = CONVERTER.toMarkdown(doc(columns(numbers("a"), numbers("b"))));

    assertThat(parsed(markdown).select("ol")).hasSize(2);
  }

  @Test
  void a_bodied_excerpt_opening_with_a_list_stays_separate_from_the_list_before_it() {
    var markdown =
        CONVERTER.toMarkdown(doc(bullets("a"), excerpt(bullets("b"), paragraph(text("c")))));

    assertThat(markdown).isEqualTo("- a\n\n" + SEPARATOR + "\n\n- b\n\nc");
    assertThat(parsed(markdown).select("ul")).hasSize(2);
  }

  @Test
  void a_bodied_excerpt_closing_with_a_list_stays_separate_from_the_list_after_it() {
    var markdown =
        CONVERTER.toMarkdown(doc(excerpt(paragraph(text("c")), bullets("a")), bullets("b")));

    assertThat(parsed(markdown).select("ul")).hasSize(2);
  }

  @Test
  void a_layout_closing_with_a_list_stays_separate_from_the_list_after_it() {
    var markdown =
        CONVERTER.toMarkdown(doc(columns(paragraph(text("p")), bullets("a")), bullets("b")));

    assertThat(parsed(markdown).select("ul")).hasSize(2);
  }

  @Test
  void a_transparent_join_inside_a_list_item_stays_separate() {
    var markdown =
        CONVERTER.toMarkdown(
            doc(bulletList(item(paragraph(text("p")), bullets("a"), excerpt(bullets("b"))))));

    assertThat(parsed(markdown).select("li > ul")).hasSize(2);
  }

  @Test
  void an_empty_column_between_two_lists_does_not_hide_the_join() {
    var section = columns(bullets("a"), paragraph(), bullets("b"));

    assertThat(parsed(CONVERTER.toMarkdown(doc(section))).select("ul")).hasSize(2);
  }

  @Test
  void a_nested_list_ending_a_block_is_not_a_trailing_list_of_another_kind() {
    var nested = bulletList(item(paragraph(text("a")), numbers("n")));

    var markdown = CONVERTER.toMarkdown(doc(nested, numbers("b")));

    assertThat(markdown).doesNotContain(SEPARATOR);
    assertThat(parsed(markdown).select("body > ol li").eachText()).containsExactly("b");
  }

  @Test
  void lists_of_different_kinds_and_non_lists_need_no_separator() {
    assertThat(CONVERTER.toMarkdown(doc(columns(bullets("a"), numbers("b")))))
        .doesNotContain(SEPARATOR);
    assertThat(CONVERTER.toMarkdown(doc(columns(bullets("a"), paragraph(text("- b"))))))
        .doesNotContain(SEPARATOR);
    assertThat(CONVERTER.toMarkdown(doc(columns(paragraph(text("1. a")), numbers("b")))))
        .doesNotContain(SEPARATOR);
    assertThat(CONVERTER.toMarkdown(doc(bullets("a"), atom("rule", "{}"), bullets("b"))))
        .doesNotContain(SEPARATOR);
    assertThat(CONVERTER.toMarkdown(doc(node("blockquote", "{}", bullets("a")), bullets("b"))))
        .doesNotContain(SEPARATOR);
  }

  @Test
  void code_that_looks_like_a_list_is_not_a_list() {
    var code = node("codeBlock", "{}", text("- a"));

    assertThat(CONVERTER.toMarkdown(doc(code, bullets("b")))).doesNotContain(SEPARATOR);
  }

  @Test
  void a_macro_list_is_kept_apart_from_a_list_after_it() {
    var markdown = CONVERTER.toMarkdown(doc(heading(2, text("H")), toc(), bullets("b")));

    assertThat(markdown).contains("- [H](#h)\n\n" + SEPARATOR + "\n\n- b");
  }
}
