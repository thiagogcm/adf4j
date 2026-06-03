package dev.nthings.adf4j.internal.parser;

import java.util.List;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.Date;
import dev.nthings.adf4j.ast.Emoji;
import dev.nthings.adf4j.ast.InlineCard;
import dev.nthings.adf4j.ast.Mention;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Status;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.ast.UnknownBlock;
import dev.nthings.adf4j.ast.UnknownInline;
import dev.nthings.adf4j.ast.UnknownMark;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AdfAstParserTests {

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final AdfAstParser parser = new AdfAstParser(mapper);

  @Test
  void unknown_block_preserves_type_and_raw_subtree() throws Exception {
    var raw = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {"type": "widget", "attrs": {"flavor": "alpha"}, "content": []}
          ]
        }
        """;
    var document = parser.parseDocument(mapper.readTree(raw));

    assertThat(document.content()).hasSize(1);
    AdfBlock block = document.content().getFirst();
    assertThat(block).isInstanceOf(UnknownBlock.class);
    var unknown = (UnknownBlock) block;
    assertThat(unknown.type()).isEqualTo("widget");
    assertThat(mapper.readTree(unknown.rawJson()))
        .as("raw JSON round-trip preserves the original subtree")
        .isEqualTo(mapper.readTree("{\"type\":\"widget\",\"attrs\":{\"flavor\":\"alpha\"},\"content\":[]}"));
  }

  @Test
  void unknown_inline_preserves_type_and_raw_subtree() throws Exception {
    var raw = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "paragraph",
              "content": [{"type": "mysteryInline", "attrs": {"foo": "bar"}}]
            }
          ]
        }
        """;
    var document = parser.parseDocument(mapper.readTree(raw));

    AdfBlock first = document.content().getFirst();
    assertThat(first).isInstanceOf(Paragraph.class);
    var paragraph = (Paragraph) first;
    assertThat(paragraph.content()).hasSize(1);
    AdfInline inline = paragraph.content().getFirst();
    assertThat(inline).isInstanceOf(UnknownInline.class);
    var unknown = (UnknownInline) inline;
    assertThat(unknown.type()).isEqualTo("mysteryInline");
    assertThat(mapper.readTree(unknown.rawJson()))
        .isEqualTo(mapper.readTree("{\"type\":\"mysteryInline\",\"attrs\":{\"foo\":\"bar\"}}"));
  }

  @Test
  void unknown_mark_preserves_type_and_raw_subtree() throws Exception {
    var raw = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "paragraph",
              "content": [
                {"type": "text", "text": "hi", "marks": [{"type": "newMark", "attrs": {"x": 1}}]}
              ]
            }
          ]
        }
        """;
    var document = parser.parseDocument(mapper.readTree(raw));

    var paragraph = (Paragraph) document.content().getFirst();
    var text = (Text) paragraph.content().getFirst();
    assertThat(text.marks()).hasSize(1);
    AdfMark mark = text.marks().getFirst();
    assertThat(mark).isInstanceOf(UnknownMark.class);
    var unknown = (UnknownMark) mark;
    assertThat(unknown.type()).isEqualTo("newMark");
    assertThat(mapper.readTree(unknown.rawJson()))
        .isEqualTo(mapper.readTree("{\"type\":\"newMark\",\"attrs\":{\"x\":1}}"));
  }

  @Test
  void parses_emoji_id_and_status_localid_and_mention_user_metadata() throws Exception {
    var raw = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "paragraph",
              "content": [
                {
                  "type": "mention",
                  "attrs": {
                    "id": "user-42",
                    "text": "@alice",
                    "userType": "DEFAULT",
                    "accessLevel": "CONTAINER",
                    "localId": "loc-1"
                  }
                },
                {
                  "type": "emoji",
                  "attrs": {"id": "1f600", "text": "😀", "shortName": ":grinning:"}
                },
                {
                  "type": "status",
                  "attrs": {"text": "Done", "color": "green", "style": "bold", "localId": "loc-2"}
                },
                {
                  "type": "date",
                  "attrs": {"timestamp": "1700000000000", "localId": "loc-3"}
                }
              ]
            }
          ]
        }
        """;
    var document = parser.parseDocument(mapper.readTree(raw));

    var paragraph = (Paragraph) document.content().getFirst();
    var mention = (Mention) paragraph.content().get(0);
    assertThat(mention.id()).isEqualTo("user-42");
    assertThat(mention.text()).isEqualTo("@alice");
    assertThat(mention.userType()).isEqualTo("DEFAULT");
    assertThat(mention.accessLevel()).isEqualTo("CONTAINER");
    assertThat(mention.localId()).isEqualTo("loc-1");

    var emoji = (Emoji) paragraph.content().get(1);
    assertThat(emoji.id()).isEqualTo("1f600");
    assertThat(emoji.shortName()).isEqualTo(":grinning:");

    var status = (Status) paragraph.content().get(2);
    assertThat(status.style()).isEqualTo("bold");
    assertThat(status.localId()).isEqualTo("loc-2");

    var date = (Date) paragraph.content().get(3);
    assertThat(date.localId()).isEqualTo("loc-3");
  }

  @Test
  void macro_params_ignore_object_entries_without_a_scalar_value() throws Exception {
    var raw = mapper.readTree(
        """
        {
          "scalar": "ok",
          "wrapped": {"value": "yes"},
          "objectNoValue": {"nested": {"deep": "true"}},
          "objectNonScalar": {"value": {"deep": "true"}},
          "nullValue": null
        }
        """);

    var params = parser.parseMacroParams(raw);

    assertThat(params.value("scalar")).isEqualTo("ok");
    assertThat(params.value("wrapped")).isEqualTo("yes");
    assertThat(params.value("objectNoValue")).isNull();
    assertThat(params.value("objectNonScalar")).isNull();
    assertThat(params.value("nullValue")).isNull();
  }

  @Test
  void attributes_preserve_array_index_positions_including_an_interior_null() throws Exception {
    // A colwidth-style array with a hole in the middle must keep its shape: index 1 stays null so
    // positional readers (e.g. column N's width) don't shift. Integral JSON numbers become Long.
    var raw = """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "paragraph",
              "content": [
                {
                  "type": "inlineCard",
                  "attrs": {"url": "https://example.com", "colwidth": [100, null, 200]}
                }
              ]
            }
          ]
        }
        """;
    var document = parser.parseDocument(mapper.readTree(raw));

    var paragraph = (Paragraph) document.content().getFirst();
    var card = (InlineCard) paragraph.content().getFirst();
    var attributes = card.attrs().attrs();

    assertThat(attributes.values().get("colwidth")).isInstanceOf(List.class);
    var colwidth = (List<?>) attributes.values().get("colwidth");
    assertThat(colwidth).hasSize(3);
    assertThat(colwidth.get(0)).isEqualTo(100L);
    assertThat(colwidth.get(1)).as("the interior null keeps its index").isNull();
    assertThat(colwidth.get(2)).isEqualTo(200L);
  }
}
