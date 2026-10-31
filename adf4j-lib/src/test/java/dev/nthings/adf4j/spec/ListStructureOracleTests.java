package dev.nthings.adf4j.spec;

import static dev.nthings.adf4j.testing.AdfNodes.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.TaskItem;
import dev.nthings.adf4j.ast.Text;
import java.util.List;
import java.util.function.Consumer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListStructureOracleTests {

  private static final AdfToMarkdown CONVERTER = AdfToMarkdown.create();

  @ParameterizedTest
  @ValueSource(strings = {"document", "panel", "listItem", "blockTask"})
  void adjacent_lists_keep_their_boundaries_and_numbering(String container) {
    var content =
        List.<AdfBlock>of(
            orderedList(1, List.of(listItem(List.of(paragraph("Alpha"))))),
            paragraph(""),
            orderedList(10, List.of(listItem(List.of(paragraph("Beta"))))));
    var document =
        new AdfDocument(
            1,
            switch (container) {
              case "panel" -> List.of(panel("info", content));
              case "listItem" -> List.of(bulletList(List.of(listItem(content))));
              case "blockTask" -> List.of(taskList(List.of(blockTaskItem("TODO", content))));
              default -> content;
            });
    assertHtml(
        document,
        html -> {
          var lists = html.select("ol");
          assertThat(lists).hasSize(2);
          assertThat(lists.get(0).select("li").eachText()).containsExactly("Alpha");
          assertThat(lists.get(1).attr("start")).isEqualTo("10");
          assertThat(lists.get(1).select("li").eachText()).containsExactly("Beta");
        });
  }

  @Test
  void html_table_keeps_empty_ordered_items_including_an_entirely_empty_list() {
    var empty = listItem(List.of(paragraph("")));
    var cell =
        tableCell(
            false,
            1,
            1,
            List.of(
                orderedList(3, List.of(empty, listItem(List.of(paragraph("Beta"))), empty)),
                orderedList(8, List.of(empty))));
    assertHtml(
        table(false, List.of(tableRow(List.of(cell)))),
        html -> {
          var lists = html.select("td > ol");
          assertThat(lists.eachAttr("start")).containsExactly("3", "8");
          assertThat(lists.get(0).select("li"))
              .extracting(Element::text)
              .containsExactly("", "Beta", "");
          assertThat(lists.get(1).select("li")).extracting(Element::text).containsExactly("");
        });
  }

  @Test
  void adjacent_bullets_tasks_and_decisions_remain_separate_lists() {
    var document =
        new AdfDocument(
            1,
            List.of(
                bulletList(List.of(listItem(List.of(paragraph("Bullet"))))),
                taskList(List.of(task("DONE", "Task"))),
                decisionList(List.of(decisionItem("DECIDED", List.of(text("Decision")))))));
    assertHtml(
        document,
        html -> {
          var lists = html.select("body > ul");
          assertThat(lists).hasSize(3);
          assertThat(lists.eachText())
              .containsExactly("Bullet", "Task", "[decision:DECIDED] Decision");
          assertThat(lists.get(1).select("input[checked]")).hasSize(1);
        });
  }

  @Test
  void sibling_nested_task_lists_remain_separate_under_the_same_parent() {
    var root =
        taskList(
            List.of(
                task("DONE", "Parent"),
                taskList(List.of(task("TODO", "First"))),
                taskList(List.of(task("TODO", "Second")))));
    assertHtml(
        root,
        html -> {
          assertThat(html.select("body > ul > li > ul").eachText())
              .containsExactly("First", "Second");
          assertThat(html.select("input[type=checkbox]")).hasSize(3);
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"panel", "blockquote", "expand"})
  void containers_inside_two_list_levels_preserve_tasks_and_decisions(String kind) {
    var content =
        List.<AdfBlock>of(
            taskList(List.of(task("DONE", "Checked"), task("TODO", "Open"))),
            paragraph("Decisions"),
            decisionList(List.of(decisionItem("DECIDED", List.of(text("Choice"))))));
    AdfBlock container =
        switch (kind) {
          case "panel" -> panel("info", content);
          case "blockquote" -> blockquote(content);
          case "expand" -> expand("Details", content);
          default -> throw new IllegalArgumentException(kind);
        };
    var selector =
        switch (kind) {
          case "panel" -> "div.markdown-alert";
          case "blockquote" -> "blockquote";
          default -> "details";
        };
    var root = ordered(10, "Outer", bullet("Inner", container));

    assertHtml(
        root,
        document -> {
          var containers = document.select("body > ol > li > ul > li > " + selector);
          assertThat(containers).hasSize(1);
          assertThat(containers.getFirst().select("ul > li").eachText())
              .containsExactly("Checked", "Open", "[decision:DECIDED] Choice");
          assertThat(containers.select("input[type=checkbox]")).hasSize(2);
          assertThat(containers.select("input[checked]")).hasSize(1);
          assertThat(document.select("pre, code")).isEmpty();
        });
  }

  @Test
  void nested_block_task_keeps_its_child_list_and_continuation_paragraph() {
    var root =
        taskList(
            List.of(
                task("DONE", "Parent"),
                taskList(
                    List.of(
                        blockTaskItem(
                            "TODO",
                            List.of(
                                paragraph("Block task"),
                                taskList(List.of(task("TODO", "Child"))),
                                paragraph("Detail"),
                                ordered(10, "Numbered", paragraph("Numbered detail"))))))));

    assertHtml(
        root,
        document -> {
          assertThat(document.select("body > ul > li > ul > li > ul > li").eachText())
              .containsExactly("Child");
          assertThat(document.select("body > ul > li > ul > li > p").eachText())
              .containsExactly("Block task", "Detail");
          assertThat(document.select("body > ul > li > ul > li > ol[start=10] > li").eachText())
              .containsExactly("Numbered Numbered detail");
          assertThat(document.select("input[type=checkbox]")).hasSize(3);
          assertThat(document.select("input[checked]")).hasSize(1);
          assertThat(document.select("pre, code")).isEmpty();
        });
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 10})
  void block_task_can_start_with_an_ordered_list_above_or_below_one(int start) {
    var root =
        taskList(
            List.of(blockTaskItem("TODO", List.of(ordered(start, "First", paragraph("Detail"))))));

    assertHtml(
        root,
        document -> {
          assertThat(document.select("body > ul > li > ol").eachAttr("start"))
              .containsExactly(Integer.toString(start));
          assertThat(document.select("body > ul > li > ol > li").eachText())
              .containsExactly("First Detail");
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"task", "decision", "blockTask"})
  void continuation_text_and_literal_markers_stay_inside_the_item(String kind) {
    var content =
        List.<AdfInline>of(
            text("Before"),
            new HardBreak(null, null),
            new HardBreak(null, null),
            text("# After"),
            new HardBreak(null, null),
            text("- literal"));
    AdfBlock item =
        switch (kind) {
          case "task" -> taskList(List.of(taskItem("TODO", content)));
          case "decision" -> decisionList(List.of(decisionItem("DECIDED", content)));
          case "blockTask" ->
              taskList(List.of(blockTaskItem("TODO", List.of(paragraphOf(content)))));
          default -> throw new IllegalArgumentException(kind);
        };

    assertHtml(
        bullet("Outer", item),
        document -> {
          var items = document.select("body > ul > li > ul > li");
          assertThat(items).hasSize(1);
          assertThat(items.getFirst().text()).endsWith("Before # After - literal");
          assertThat(document.select("li")).hasSize(2);
          assertThat(document.select("body > p, body > ul > li > p, h1, pre, code")).isEmpty();
        });
  }

  @ParameterizedTest
  @ValueSource(ints = {9, 99})
  void ordered_marker_width_changes_keep_children_under_their_own_items(int start) {
    var root =
        orderedList(
            start,
            List.of(
                listItem(
                    List.of(paragraph("First"), bullet("First child", paragraph("First detail")))),
                listItem(
                    List.of(
                        paragraph("Second"), bullet("Second child", paragraph("Second detail"))))));

    assertHtml(
        root,
        document -> {
          var items = document.select("body > ol > li");
          assertThat(items).hasSize(2);
          assertThat(items.get(0).select("ul > li").eachText())
              .containsExactly("First child First detail");
          assertThat(items.get(1).select("ul > li").eachText())
              .containsExactly("Second child Second detail");
          assertThat(document.select("ol").eachAttr("start"))
              .containsExactly(Integer.toString(start));
          assertThat(document.select("pre, code")).isEmpty();
        });
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 10})
  void html_table_lists_keep_their_start_and_nested_checkboxes(int start) {
    var lists =
        ordered(
            start,
            "Outer",
            ordered(
                start,
                "Inner",
                panel("info", List.of(taskList(List.of(task("DONE", "Checked")))))));
    var root = table(false, List.of(tableRow(List.of(tableCell(false, 2, 1, List.of(lists))))));

    assertHtml(
        root,
        document -> {
          var orderedLists = document.select("td[colspan=2] ol");
          assertThat(orderedLists).hasSize(2);
          assertThat(orderedLists.eachAttr("start"))
              .containsExactlyElementsOf(start == 1 ? List.of() : List.of("" + start, "" + start));
          assertThat(
                  document.select("td > ol > li > ol > li > div.markdown-alert ul > li").eachText())
              .containsExactly("Checked");
          assertThat(document.select("input[checked]")).hasSize(1);
          assertThat(document.select("pre, code")).isEmpty();
        });
  }

  private static void assertHtml(AdfBlock block, Consumer<Document> assertions) {
    assertHtml(new AdfDocument(1, List.of(block)), assertions);
  }

  private static void assertHtml(AdfDocument document, Consumer<Document> assertions) {
    var markdown = CONVERTER.convert(document).body();
    assertions.accept(Jsoup.parse(CommonMarkTestSupport.toHtml(markdown)));
    assertions.accept(Jsoup.parse(CommonMarkTestSupport.roundTripToHtml(markdown)));
  }

  private static Text text(String value) {
    return new Text(value, List.of());
  }

  private static Paragraph paragraph(String value) {
    return paragraphOf(List.of(text(value)));
  }

  private static TaskItem task(String state, String value) {
    return taskItem(state, List.of(text(value)));
  }

  private static BulletList bullet(String value, AdfBlock child) {
    return bulletList(List.of(listItem(List.of(paragraph(value), child))));
  }

  private static OrderedList ordered(int start, String value, AdfBlock child) {
    return orderedList(start, List.of(listItem(List.of(paragraph(value), child))));
  }
}
