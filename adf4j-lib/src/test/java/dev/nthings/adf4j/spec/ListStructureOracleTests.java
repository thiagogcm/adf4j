package dev.nthings.adf4j.spec;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.BlockTaskItem;
import dev.nthings.adf4j.ast.Blockquote;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.Expand;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Panel;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;
import dev.nthings.adf4j.ast.TaskItem;
import dev.nthings.adf4j.ast.TaskList;
import dev.nthings.adf4j.ast.Text;
import java.util.List;
import java.util.function.Consumer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListStructureOracleTests {

  private static final AdfToMarkdown CONVERTER = AdfToMarkdown.create();

  @ParameterizedTest
  @ValueSource(strings = {"panel", "blockquote", "expand"})
  void containers_inside_two_list_levels_preserve_tasks_and_decisions(String kind) {
    var content =
        List.<AdfBlock>of(
            new TaskList(List.of(task("DONE", "Checked"), task("TODO", "Open"))),
            paragraph("Decisions"),
            new DecisionList(List.of(new DecisionItem("DECIDED", List.of(text("Choice"))))));
    AdfBlock container =
        switch (kind) {
          case "panel" -> new Panel("info", content);
          case "blockquote" -> new Blockquote(content);
          case "expand" -> new Expand("Details", content);
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
        new TaskList(
            List.of(
                task("DONE", "Parent"),
                new TaskList(
                    List.of(
                        new BlockTaskItem(
                            "TODO",
                            List.of(
                                paragraph("Block task"),
                                new TaskList(List.of(task("TODO", "Child"))),
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
        new TaskList(
            List.of(
                new BlockTaskItem("TODO", List.of(ordered(start, "First", paragraph("Detail"))))));

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
            new HardBreak(),
            new HardBreak(),
            text("# After"),
            new HardBreak(),
            text("- literal"));
    AdfBlock item =
        switch (kind) {
          case "task" -> new TaskList(List.of(new TaskItem("TODO", content)));
          case "decision" -> new DecisionList(List.of(new DecisionItem("DECIDED", content)));
          case "blockTask" ->
              new TaskList(
                  List.of(new BlockTaskItem("TODO", List.of(new Paragraph(content, List.of())))));
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
        new OrderedList(
            start,
            List.of(
                new ListItem(
                    List.of(paragraph("First"), bullet("First child", paragraph("First detail")))),
                new ListItem(
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
                new Panel("info", List.of(new TaskList(List.of(task("DONE", "Checked")))))));
    var root =
        new Table(
            false,
            List.of(new TableRow(List.of(new TableCell(false, 2, 1, null, List.of(lists))))));

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
    var markdown = CONVERTER.convert(new AdfDocument(1, List.of(block))).body();
    assertions.accept(Jsoup.parse(CommonMarkTestSupport.toHtml(markdown)));
    assertions.accept(Jsoup.parse(CommonMarkTestSupport.roundTripToHtml(markdown)));
  }

  private static Text text(String value) {
    return new Text(value, List.of());
  }

  private static Paragraph paragraph(String value) {
    return new Paragraph(List.of(text(value)), List.of());
  }

  private static TaskItem task(String state, String value) {
    return new TaskItem(state, List.of(text(value)));
  }

  private static BulletList bullet(String value, AdfBlock child) {
    return new BulletList(List.of(new ListItem(List.of(paragraph(value), child))));
  }

  private static OrderedList ordered(int start, String value, AdfBlock child) {
    return new OrderedList(start, List.of(new ListItem(List.of(paragraph(value), child))));
  }
}
