package dev.nthings.adf4j.ast;

import static dev.nthings.adf4j.ast.Adf.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.networknt.schema.Error;
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
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AdfTests {

  private static final String CONFLUENCE_MACRO = "com.atlassian.confluence.macro.core";
  private static final String PINNED_ID = "4c9cd0d5-9afb-37a8-8159-d62cdafcf5b5";

  private static List<Error> violations(AdfDocument document) throws IOException {
    var schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4)
            .getSchema(Files.readString(Path.of("../docs/spec/adf-schema.json")));
    return schema.validate(AdfJsonComparison.MAPPER.readTree(AdfJson.write(document)));
  }

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
            blockquote("plain quote"),
            codeBlock("code"),
            codeBlock("java", "var x = 1;"),
            rule(),
            expand("More", paragraph("hidden")),
            expand("Plain", "hidden"),
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
            table(tableRow(tableCell("sized").withColwidth(200))).withWidth(760),
            mediaSingle(media("file-2", "collection").withAlt("A chart").withSize(800, 600)),
            mediaSingle(
                    externalMedia("https://i.example/a.png").withMarks(border(2, "#000000")),
                    caption("Caption"))
                .withLayout(MediaLayout.WIDE)
                .withWidth(50),
            mediaSingle(externalMedia("https://i.example/b.png"), caption(text("Inline caption"))),
            mediaSingle(externalMedia("https://i.example/c.png"))
                .withPixelWidth(640)
                .withLink(link("https://f.example")),
            mediaGroup(media("file-3", "collection"), media("file-4", "collection")),
            layoutSection(
                layoutColumn(50, paragraph("left")), layoutColumn(50, paragraph("right"))),
            layoutSection(
                layoutColumn(33.33, paragraph("1")),
                layoutColumn(33.33, paragraph("2")),
                layoutColumn(33.34, paragraph("3"))),
            extension("ext.type", "plain", Map.of("a", "b")),
            extension("ext.type", "no-parameters"),
            paragraph(inlineExtension("ext.type", "no-parameters")),
            bodiedExtension("ext.type", "bodied", Map.of(), paragraph("body")),
            syncBlock("resource-1"),
            bodiedSyncBlock("resource-2", taskList(taskItem(TaskState.TODO, "synced"))),
            blockCard("https://d.example"),
            embedCard("https://e.example", MediaLayout.CENTER)));
    for (var type : PanelType.values()) {
      blocks.add(panel(type, "in a panel"));
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
    assertThat(violations(everything())).isEmpty();
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
  void empty_text_is_no_node_wherever_it_appears() {
    var blank = text("");
    var noStatus = status("", StatusColor.GREEN);

    assertThat(paragraph("").content()).isEmpty();
    assertThat(paragraph(blank, noStatus, text("kept")).content()).containsExactly(text("kept"));
    assertThat(heading(1, blank).content()).isEmpty();
    assertThat(taskItem(TaskState.TODO, blank).content()).isEmpty();
    assertThat(decisionItem(blank).content()).isEmpty();
    assertThat(caption(blank).content()).isEmpty();
    assertThat(codeBlock("java", "").content()).isEmpty();
    assertThat(tableCell("").content()).containsExactly(paragraph());
  }

  @Test
  void every_container_takes_content_built_from_data() throws IOException {
    var words = List.of("a", "b");
    var inline = words.stream().map(Adf::text).toList();
    var paragraphs = words.stream().map(Adf::paragraph).toList();
    var items = words.stream().map(Adf::listItem).toList();
    var images = words.stream().map(word -> media(word, "c")).toList();
    var columns = paragraphs.stream().map(p -> layoutColumn(50, List.of(p))).toList();

    var document =
        doc(
            List.of(
                paragraph(inline),
                heading(1, inline),
                blockquote(paragraphs),
                panel(PanelType.INFO, paragraphs),
                expand("t", paragraphs),
                bulletList(items),
                orderedList(items),
                bulletList(listItem(paragraphs)),
                taskList(List.of(taskItem(TaskState.TODO, inline))),
                taskList(
                    taskItem(TaskState.TODO, "first"), blockTaskItem(TaskState.TODO, paragraphs)),
                decisionList(List.of(decisionItem(inline))),
                table(
                    List.of(
                        tableRow(List.of(tableHeader(paragraphs), tableHeader(paragraphs))),
                        tableRow(
                            tableCell(paragraphs),
                            tableCell(List.of(nestedExpand("t", paragraphs)))))),
                mediaSingle(images.getFirst(), caption(inline)),
                mediaGroup(images),
                layoutSection(columns),
                bodiedExtension("t", "k", Map.of(), paragraphs),
                bodiedSyncBlock("r", paragraphs)));

    assertThat(violations(document)).isEmpty();
  }

  @Test
  void a_required_child_missing_from_data_fails_naming_the_node() {
    List<Runnable> builds =
        List.of(
            () -> blockquote(List.of()),
            () -> panel(PanelType.INFO, List.of()),
            () -> expand("t", List.of()),
            () -> nestedExpand("t", List.of()),
            () -> bulletList(List.of()),
            () -> orderedList(List.of()),
            () -> listItem(List.of()),
            () -> taskList(List.of()),
            () -> blockTaskItem(TaskState.TODO, List.of()),
            () -> decisionList(List.of()),
            () -> table(List.of()),
            () -> tableRow(List.of()),
            () -> tableCell(List.of()),
            () -> tableHeader(List.of()),
            () -> mediaGroup(List.of()),
            () -> layoutColumn(50, List.of()),
            () -> bodiedExtension("t", "k", Map.of(), List.of()),
            () -> bodiedSyncBlock("r", List.of()));

    assertThat(builds)
        .allSatisfy(
            build ->
                assertThatIllegalArgumentException()
                    .isThrownBy(build::run)
                    .withMessageContaining("needs at least one child"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> layoutSection(List.of(layoutColumn(100, paragraph("x")))))
        .withMessageContaining("2 or 3 columns");
  }

  @Test
  void each_attribute_reads_back_through_its_accessor() {
    var cell = tableCell("x").withColspan(2).withRowspan(3).withBackground("#eeeeee");
    var table = table(tableRow(cell)).withLayout(TableLayout.ALIGN_START).withNumberColumn();
    var single = mediaSingle(externalMedia("https://i")).withLayout(MediaLayout.WRAP_LEFT);
    var image = media("id", "col").withAlt("alt").withSize(800, 600);

    assertThat(sub().subSupType()).isEqualTo("sub");
    assertThat(sup().subSupType()).isEqualTo("sup");
    assertThat(link("https://a", "T").title()).isEqualTo("T");
    assertThat(cell.colspan()).isEqualTo(2);
    assertThat(cell.rowspan()).isEqualTo(3);
    assertThat(cell.background()).isEqualTo("#eeeeee");
    assertThat(tableHeader("x").header()).isTrue();
    assertThat(table.numberColumnEnabled()).isTrue();
    assertThat(table.attrs().text("layout")).isEqualTo("align-start");
    assertThat(orderedList(listItem("x")).withOrder(5).order()).isEqualTo(5);
    assertThat(single.layout()).isEqualTo("wrap-left");
    assertThat(single.withPixelWidth(640).widthType()).isEqualTo("pixel");
    assertThat(single.withPixelWidth(640).withWidth(50).widthType()).isEqualTo("percentage");
    assertThat(single.withPixelWidth(640).width()).isEqualTo("640");
    assertThat(single.withLink(link("https://a")).marks()).containsExactly(link("https://a"));
    assertThat(table.withWidth(760).attrs().number("width", 0)).isEqualTo(760);
    assertThat(cell.withColwidth(200, 300).attrs().values().get("colwidth"))
        .isEqualTo(List.of(200L, 300L));
    assertThat(nestedExpand("t", "x").content()).containsExactly(paragraph("x"));
    assertThat(bodiedExtension("t", "k", paragraph("x")).extensionKey()).isEqualTo("k");
    assertThat(image.id()).isEqualTo("id");
    assertThat(image.collection()).isEqualTo("col");
    assertThat(image.alt()).isEqualTo("alt");
    assertThat(image.width()).isEqualTo("800");
    assertThat(image.height()).isEqualTo("600");
    assertThat(heading(3, "x").level()).isEqualTo(3);
    assertThat(codeBlock("java", "x").language()).isEqualTo("java");
    assertThat(panel(PanelType.SUCCESS, "x").panelType()).isEqualTo("success");
    assertThat(expand("t", "x").title()).isEqualTo("t");
    assertThat(layoutColumn(33.33, paragraph("x")).width()).isEqualTo(33.33);
    assertThat(status("s", StatusColor.RED).color()).isEqualTo("red");
    assertThat(mention("id", "@A").text()).isEqualTo("@A");
    assertThat(date(LocalDate.of(1999, 12, 31)).timestamp()).isEqualTo("946598400000");
  }

  @Test
  void a_confluence_macro_reads_back_through_the_extension_api() {
    var toc =
        extension(
            CONFLUENCE_MACRO, "toc", macroParameters(Map.of("minLevel", "2", "maxLevel", "3")));

    assertThat(toc.extensionKey()).isEqualTo("toc");
    assertThat(toc.macroParams().values())
        .containsOnly(Map.entry("minLevel", "2"), Map.entry("maxLevel", "3"));
  }

  @Test
  void extension_parameters_write_in_sorted_order_whatever_the_map_iterates() {
    var nested = new LinkedHashMap<String, Object>();
    nested.put("z", 3);
    nested.put("y", 4);
    var parameters = new LinkedHashMap<String, Object>();
    parameters.put("c", 1);
    parameters.put("b", List.of(nested));
    parameters.put("a", nested);

    assertThat(AdfJson.write(doc(extension("t", "k", parameters))))
        .contains("\"parameters\":{\"a\":{\"y\":4,\"z\":3},\"b\":[{\"y\":4,\"z\":3}],\"c\":1}");
  }

  @Test
  void local_ids_reach_every_container_and_stay_valid() throws IOException {
    var document =
        doc(
            panel(PanelType.INFO, tasks("panel")),
            expand("t", tasks("expand")),
            blockquote(bulletList(listItem(tasks("quote")))),
            orderedList(listItem(tasks("list"))),
            table(tableRow(tableCell(nestedExpand("t", tasks("cell"))))),
            layoutSection(
                layoutColumn(50, tasks("left")),
                layoutColumn(50, decisionList(decisionItem("right")))),
            bodiedExtension("t", "k", Map.of(), tasks("extension")),
            bodiedSyncBlock("r", tasks("sync")),
            syncBlock("s"),
            taskList(taskItem(TaskState.TODO, "outer"), tasks("nested")));

    var written = AdfJson.write(document);

    assertThat(violations(document)).isEmpty();
    assertThat(allIds(written)).hasSize(24).doesNotHaveDuplicates();
  }

  private static TaskList tasks(String text) {
    return taskList(taskItem(TaskState.TODO, text));
  }

  private static List<String> allIds(String json) {
    return Pattern.compile("\"localId\":\"([^\"]+)\"")
        .matcher(json)
        .results()
        .map(match -> match.group(1))
        .toList();
  }

  @Test
  void a_generated_id_is_pinned_so_a_change_to_it_is_deliberate() {
    var document = doc(taskList(taskItem(TaskState.TODO, text("Review", strong()))));

    assertThat(ids(document)).containsExactly(PINNED_ID);
  }

  @Test
  void a_generated_id_avoids_ids_already_in_the_document() {
    var built = doc(taskList(taskItem(TaskState.TODO, "Review")));
    var reused = (TaskList) built.content().getFirst();

    var document = doc(reused, taskList(taskItem(TaskState.TODO, "Review")));

    assertThat(allIds(AdfJson.write(document))).hasSize(4).doesNotHaveDuplicates();
  }

  @Test
  void the_same_node_used_twice_gets_two_ids() {
    var repeated = taskList(taskItem(TaskState.TODO, "Review"));

    assertThat(allIds(AdfJson.write(doc(repeated, repeated)))).hasSize(4).doesNotHaveDuplicates();
  }

  @Test
  void a_parsed_document_takes_new_content_and_keeps_what_it_had() {
    var original =
        """
        {"type":"doc","version":1,"content":[
          {"type":"futureNode","attrs":{"x":1}},
          {"type":"taskList","attrs":{"localId":"tasks"},"content":[
            {"type":"taskItem","attrs":{"localId":"task","state":"DONE"},
             "content":[{"type":"text","text":"Review"}]}]}]}
        """;
    var parsed = AdfJson.parse(original).document();

    var appended = parsed.append(paragraph("New"));

    assertThat(appended.content()).hasSize(3);
    assertThat(appended.content().subList(0, 2)).isEqualTo(parsed.content());
  }

  @Test
  void appended_ids_avoid_generated_ids_already_in_a_parsed_document() {
    var published = AdfJson.write(doc(taskList(taskItem(TaskState.TODO, "Review"))));
    var parsed = AdfJson.parse(published).document();

    var appended = parsed.append(taskList(taskItem(TaskState.TODO, "Review")));

    assertThat(allIds(AdfJson.write(appended))).hasSize(4).doesNotHaveDuplicates();
  }

  @Test
  void attrs_keep_their_order_so_written_json_is_stable() {
    assertThat(AdfJson.write(doc(paragraph(status("Done", StatusColor.GREEN)))))
        .contains("\"attrs\":{\"text\":\"Done\",\"color\":\"green\"}");
  }
}
