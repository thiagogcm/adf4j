package dev.nthings.adf4j.ast;

import static dev.nthings.adf4j.ast.Adf.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.testing.AdfJsonComparison;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdfTests {

  private static final String CONFLUENCE_MACRO = "com.atlassian.confluence.macro.core";

  private static AdfDocument everything() {
    var blocks = new ArrayList<DocContent>();
    blocks.addAll(
        List.of(
            heading(1, "Title"),
            heading(2, text("Inline"), emoji(":smile:")).withMarks(alignment(TextAlign.CENTER)),
            paragraph("plain"),
            paragraph(
                    text("all marks", strong(), em(), strike(), underline(), sub()),
                    text("code", code()),
                    text("sup", sup()),
                    text("color", textColor("#ff0000"), backgroundColor("#00ff00")),
                    text("link", link("https://a.example")),
                    text("titled", link("https://b.example", "Title")),
                    text("comment", annotation("thread-1")),
                    hardBreak(),
                    mention("user-1"),
                    mention("user-2", "@Ana"),
                    date(LocalDate.of(2026, 11, 1)),
                    placeholder("Type here"),
                    inlineCard("https://c.example"),
                    mediaInline("file-1", "collection"),
                    inlineExtension("ext.type", "inline", Map.of("a", 1)))
                .withMarks(indentation(2)),
            paragraph("small").withMarks(small()),
            blockquote(paragraph("quoted"), bulletList(listItem("in quote"))),
            codeBlock("code"),
            codeBlock("java", "var x = 1;"),
            rule(),
            expand("More", paragraph("hidden")),
            bulletList(
                listItem(paragraph("parent"), bulletList(listItem("child"))),
                listItem(codeBlock("x"))),
            orderedList(listItem("three")).withOrder(3),
            taskList(
                taskItem(TaskState.TODO, "plain"),
                taskItem(TaskState.DONE, text("inline")),
                taskList(taskItem(TaskState.TODO, "nested"))),
            taskList(
                taskItem(TaskState.TODO, "first"),
                blockTaskItem(TaskState.DONE, paragraph("blocks"))),
            decisionList(decisionItem("plain"), decisionItem(text("inline"))),
            table(
                    tableRow(tableHeader("A"), tableHeader(paragraph("B"))),
                    tableRow(
                        tableCell("wide").withColspan(2).withBackground("#eeeeee"),
                        tableCell(nestedExpand("Details", paragraph("x"))).withRowspan(2)))
                .withLayout(TableLayout.FULL_WIDTH)
                .withNumberColumn(),
            mediaSingle(media("file-2", "collection").withAlt("A chart").withSize(800, 600)),
            mediaSingle(
                    externalMedia("https://i.example/a.png").withMarks(border(2, "#000000")),
                    caption("Caption"))
                .withLayout(MediaLayout.WIDE)
                .withWidth(50),
            mediaSingle(externalMedia("https://i.example/b.png"), caption(text("Inline caption"))),
            mediaGroup(media("file-3", "collection"), media("file-4", "collection")),
            layoutSection(
                layoutColumn(50, paragraph("left")), layoutColumn(50, paragraph("right"))),
            layoutSection(
                layoutColumn(33.33, paragraph("1")),
                layoutColumn(33.33, paragraph("2")),
                layoutColumn(33.34, paragraph("3"))),
            extension("ext.type", "plain", Map.of("a", "b")),
            bodiedExtension("ext.type", "bodied", Map.of(), paragraph("body")),
            syncBlock("resource-1"),
            bodiedSyncBlock("resource-2", taskList(taskItem(TaskState.TODO, "synced"))),
            blockCard("https://d.example"),
            embedCard("https://e.example", MediaLayout.CENTER)));
    for (var type : PanelType.values()) {
      blocks.add(panel(type, paragraph("in a panel")));
    }
    var colors = Arrays.stream(StatusColor.values()).map(color -> status("s", color));
    blocks.add(paragraph(colors.toList()));
    for (var layout : MediaLayout.values()) {
      blocks.add(mediaSingle(media("f", "c")).withLayout(layout));
    }
    for (var layout : TableLayout.values()) {
      blocks.add(table(tableRow(tableCell("x"))).withLayout(layout));
    }
    return doc(blocks);
  }

  @Test
  void everything_the_builder_makes_conforms_to_the_schema() throws IOException {
    var schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4)
            .getSchema(Files.readString(Path.of("../docs/spec/adf-schema.json")));

    var written = AdfJsonComparison.MAPPER.readTree(AdfJson.write(everything()));

    assertThat(schema.validate(written)).isEmpty();
  }

  @Test
  void a_built_document_equals_its_parsed_json() {
    var document = everything();

    var parsed = AdfJson.parse(AdfJson.write(document));

    assertThat(parsed.diagnostics()).isEmpty();
    assertThat(parsed.document()).isEqualTo(document);
  }

  @Test
  void a_built_document_renders_like_a_parsed_one() {
    var document =
        doc(
            heading(2, "Release notes"),
            paragraph(
                text("Shipped by "),
                mention("id", "@Ana"),
                text(" on "),
                date(LocalDate.of(2026, 11, 1))),
            bulletList(
                listItem(paragraph(text("Faster", strong()), text(" search"))),
                listItem(paragraph(text("Docs", link("https://docs.example"))))),
            panel(PanelType.WARNING, paragraph("Restart required")),
            codeBlock("java", "var x = 1;"),
            table(
                tableRow(tableHeader("Task"), tableHeader("Owner")),
                tableRow(tableCell("Search"), tableCell(""))));

    assertThat(AdfToMarkdown.create().convert(document).body())
        .isEqualTo(
            """
            ## Release notes

            Shipped by @Ana on 2026-11-01

            - **Faster** search
            - [Docs](https://docs.example)

            > [!WARNING]
            > Restart required

            ```java
            var x = 1;
            ```

            | Task   | Owner |
            | ------ | ----- |
            | Search |       |""");
  }

  @Test
  void empty_data_makes_no_text_node() {
    assertThat(paragraph("").content()).isEmpty();
    assertThat(heading(1, "").content()).isEmpty();
    assertThat(codeBlock("java", "").content()).isEmpty();
    assertThat(tableCell("").content()).containsExactly(paragraph());
    assertThat(caption("").content()).isEmpty();
  }

  @Test
  void content_built_from_data_uses_the_list_overloads() {
    var rows = List.of("a", "b");

    var bullets = bulletList(rows.stream().map(Adf::listItem).toList());
    var numbers = orderedList(rows.stream().map(Adf::listItem).toList());
    var table =
        table(rows.stream().map(row -> tableRow(List.of(tableCell(row), tableCell("x")))).toList());
    var tasks = taskList(rows.stream().map(row -> taskItem(TaskState.TODO, row)).toList());
    var decisions = decisionList(rows.stream().map(Adf::decisionItem).toList());

    assertThat(bullets.items()).hasSize(2);
    assertThat(numbers.items()).hasSize(2);
    assertThat(table.rows()).hasSize(2);
    assertThat(table.rows().getFirst().cells()).hasSize(2);
    assertThat(tasks.items()).hasSize(2);
    assertThat(decisions.items()).hasSize(2);
  }

  @Test
  void rebuilding_the_same_content_writes_the_same_json() {
    assertThat(AdfJson.write(everything())).isEqualTo(AdfJson.write(everything()));
  }

  @Test
  void local_ids_reach_nodes_nested_in_other_containers() {
    var document =
        doc(
            panel(PanelType.INFO, taskList(taskItem(TaskState.TODO, "in a panel"))),
            table(tableRow(tableCell(decisionList(decisionItem("in a cell"))))),
            bulletList(listItem(taskList(taskItem(TaskState.TODO, "in a list")))));

    assertThat(AdfJson.write(document).split("localId", -1)).hasSize(7);
  }

  @Test
  void identical_items_get_different_ids() {
    var document =
        doc(taskList(taskItem(TaskState.TODO, "Review"), taskItem(TaskState.TODO, "Review")));

    assertThat(ids(document)).doesNotHaveDuplicates().hasSize(2);
  }

  @Test
  void a_task_keeps_its_id_when_ticked_or_when_other_tasks_change() {
    var original = ids(doc(taskList(taskItem(TaskState.TODO, "a"), taskItem(TaskState.TODO, "b"))));

    var ticked = ids(doc(taskList(taskItem(TaskState.DONE, "a"), taskItem(TaskState.TODO, "b"))));
    var inserted =
        ids(
            doc(
                taskList(
                    taskItem(TaskState.TODO, "new"),
                    taskItem(TaskState.TODO, "a"),
                    taskItem(TaskState.TODO, "b"))));
    var edited = ids(doc(taskList(taskItem(TaskState.TODO, "a!"), taskItem(TaskState.TODO, "b"))));

    assertThat(ticked).isEqualTo(original);
    assertThat(inserted.subList(1, 3)).isEqualTo(original);
    assertThat(edited.getLast()).isEqualTo(original.getLast());
    assertThat(edited.getFirst()).isNotEqualTo(original.getFirst());
  }

  @Test
  void an_id_set_by_the_caller_is_kept() {
    var document =
        doc(
            taskList(taskItem(TaskState.TODO, "a").withLocalId("task")).withLocalId("tasks"),
            taskList(blockTaskItem(TaskState.TODO, paragraph("b")).withLocalId("block")),
            decisionList(decisionItem("c").withLocalId("decision")).withLocalId("decisions"));

    var written = AdfJson.write(document);

    assertThat(written)
        .contains("\"task\"", "\"tasks\"", "\"block\"", "\"decision\"", "\"decisions\"");
  }

  private static List<String> ids(AdfDocument document) {
    var tasks = (TaskList) document.content().getFirst();
    return tasks.items().stream().map(item -> item.attrs().text("localId")).toList();
  }

  @Test
  void a_confluence_macro_reads_back_through_the_extension_api() {
    var params = new LinkedHashMap<String, String>();
    params.put("minLevel", "2");
    params.put("maxLevel", "3");

    var toc = extension(CONFLUENCE_MACRO, "toc", macroParameters(params));

    assertThat(toc.extensionKey()).isEqualTo("toc");
    assertThat(toc.macroParams().values()).containsExactlyEntriesOf(params);
  }

  @Test
  void attrs_keep_their_order_so_written_json_is_stable() {
    assertThat(AdfJson.write(doc(paragraph(status("Done", StatusColor.GREEN)))))
        .contains("\"attrs\":{\"text\":\"Done\",\"color\":\"green\"}");
  }

  @Test
  void a_date_renders_as_the_day_it_was_built_from() {
    var body = AdfToMarkdown.create().convert(doc(paragraph(date(LocalDate.of(1999, 12, 31)))));

    assertThat(body.body()).isEqualTo("1999-12-31");
  }
}
