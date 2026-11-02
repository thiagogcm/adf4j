package dev.nthings.adf4j.ast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AstRecordContractTests {

  @Test
  void container_defensively_copies_content_and_marks() {
    var content = new ArrayList<AdfBlock>();
    content.add(new Paragraph(Attributes.empty(), List.of(new Text("kept", List.of())), List.of()));
    var marks = new ArrayList<AdfMark>();
    marks.add(new Strong(Attributes.empty()));

    var node = new BodiedSyncBlock(Attributes.empty(), content, marks);
    content.clear();
    marks.clear();

    assertThat(node.content()).hasSize(1);
    assertThat(node.marks()).hasSize(1);
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> node.content().clear());
  }

  @Test
  void card_accessors_are_null_for_empty_attrs() {
    List<AdfCard> cards =
        List.of(
            new InlineCard(Attributes.empty(), List.of()),
            new BlockCard(Attributes.empty(), List.of()),
            new EmbedCard(Attributes.empty(), List.of()));

    assertThat(cards)
        .allSatisfy(
            card -> {
              assertThat(card.url()).isNull();
              assertThat(card.datasourceId()).isNull();
              assertThat(card.localId()).isNull();
              assertThat(card.title()).isNull();
            });
  }

  @Test
  void card_accessors_read_attrs_with_the_data_url_fallback() {
    var data = Map.of("url", "https://example.com/data", "name", "Title");

    var fallback = new InlineCard(new Attributes(Map.of("url", " ", "data", data)), List.of());
    var direct =
        new BlockCard(
            new Attributes(
                Map.of(
                    "url",
                    "https://example.com/own",
                    "data",
                    data,
                    "localId",
                    "local",
                    "datasource",
                    Map.of("id", "source"))),
            List.of());

    assertThat(fallback.url()).isEqualTo("https://example.com/data");
    assertThat(fallback.title()).isEqualTo("Title");
    assertThat(direct.url()).isEqualTo("https://example.com/own");
    assertThat(direct.localId()).isEqualTo("local");
    assertThat(direct.datasourceId()).isEqualTo("source");
  }

  @Test
  void media_accessors_read_attrs_on_both_media_nodes() {
    var attrs =
        new Attributes(
            Map.ofEntries(
                Map.entry("type", "file"),
                Map.entry("id", "file-1"),
                Map.entry("localId", "local-1"),
                Map.entry("url", "https://example.com/a.png"),
                Map.entry("collection", "contentId-1"),
                Map.entry("alt", "Alt"),
                Map.entry("width", 800),
                Map.entry("height", "400"),
                Map.entry("mediaType", "image"),
                Map.entry("__fileMimeType", "image/png"),
                Map.entry("__fileName", "a.png"),
                Map.entry("name", "Name")));

    List<AdfMedia> nodes = List.of(new Media(attrs, List.of()), new MediaInline(attrs, List.of()));

    assertThat(nodes)
        .allSatisfy(
            media -> {
              assertThat(media.sourceType()).isEqualTo("file");
              assertThat(media.id()).isEqualTo("file-1");
              assertThat(media.localId()).isEqualTo("local-1");
              assertThat(media.url()).isEqualTo("https://example.com/a.png");
              assertThat(media.collection()).isEqualTo("contentId-1");
              assertThat(media.alt()).isEqualTo("Alt");
              assertThat(media.width()).isEqualTo("800");
              assertThat(media.height()).isEqualTo("400");
              assertThat(media.mediaType()).isEqualTo("image");
              assertThat(media.fileMimeType()).isEqualTo("image/png");
              assertThat(media.fileName()).isEqualTo("a.png");
              assertThat(media.name()).isEqualTo("Name");
            });
    assertThat(nodes).extracting(AdfNode::type).containsExactly("media", "mediaInline");
  }

  @Test
  void media_accessors_are_null_for_empty_attrs() {
    var media = new Media(Attributes.empty(), List.of());

    assertThat(media.sourceType()).isNull();
    assertThat(media.id()).isNull();
    assertThat(media.fileName()).isNull();
  }

  @Test
  void unknown_nodes_and_marks_share_their_type_and_raw_json() {
    List<AdfUnknown> unknowns =
        List.of(
            new UnknownBlock("mystery", "{\"a\":1}"),
            new UnknownInline("mystery", "{\"a\":1}"),
            new UnknownMark("mystery", "{\"a\":1}"));

    assertThat(unknowns).extracting(AdfUnknown::type).containsOnly("mystery");
    assertThat(unknowns).extracting(AdfUnknown::rawJson).containsOnly("{\"a\":1}");
  }

  @Test
  void each_record_reports_its_json_type() {
    assertThat(new TableCell(false, Attributes.empty(), List.of(), List.of()).type())
        .isEqualTo("tableCell");
    assertThat(new TableCell(true, Attributes.empty(), List.of(), List.of()).type())
        .isEqualTo("tableHeader");
    assertThat(new SubSup(Attributes.empty()).type()).isEqualTo("subsup");
    assertThat(new AdfDocument(1, List.of()).type()).isEqualTo("doc");
    assertThat(new UnknownBlock("mystery", "{}").type()).isEqualTo("mystery");
  }

  @Test
  void accessors_read_attrs_leniently_without_changing_them() {
    var attrs = new Attributes(Map.of("level", "9", "colspan", 0));

    var heading = new Heading(attrs, List.of(), List.of());
    var cell = new TableCell(false, attrs, List.of(), List.of());

    assertThat(heading.level()).isEqualTo(9);
    assertThat(cell.colspan()).isEqualTo(1);
    assertThat(heading.attrs()).isSameAs(attrs);
  }

  @Test
  void filtered_accessors_hide_off_schema_children_that_content_keeps() {
    var stray = new UnknownBlock("mystery", "{}");
    var item = new ListItem(Attributes.empty(), List.of(), List.of());

    var list = new BulletList(Attributes.empty(), List.of(stray, item), List.of());

    assertThat(list.content()).containsExactly(stray, item);
    assertThat(list.items()).containsExactly(item);
  }
}
