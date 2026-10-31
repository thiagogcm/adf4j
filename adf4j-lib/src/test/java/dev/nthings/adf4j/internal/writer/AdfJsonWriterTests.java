package dev.nthings.adf4j.internal.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Strong;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.ast.UnknownBlock;
import dev.nthings.adf4j.ast.UnknownInline;
import dev.nthings.adf4j.ast.UnknownMark;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdfJsonWriterTests {

  private final AdfJsonWriter writer = AdfJsonWriter.createDefault();

  @Test
  void writes_type_attrs_content_and_marks_omitting_empty_attrs_and_marks() {
    var text = new Text("hi", List.of(new Strong(null)));
    var document =
        new AdfDocument(
            1, List.of(new Paragraph(new Attributes(Map.of("a", 1)), List.of(text), null)));

    assertThat(writer.write(document))
        .isEqualTo(
            "{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"paragraph\","
                + "\"attrs\":{\"a\":1},\"content\":[{\"type\":\"text\",\"text\":\"hi\","
                + "\"marks\":[{\"type\":\"strong\"}]}]}]}");
  }

  @Test
  void a_container_always_writes_content_and_a_leaf_never_does() {
    var document =
        new AdfDocument(
            2,
            List.of(
                new TableCell(true, null, null, null),
                new Paragraph(null, List.of(new HardBreak(null, null)), null)));

    assertThat(writer.write(document))
        .isEqualTo(
            "{\"type\":\"doc\",\"version\":2,\"content\":[{\"type\":\"tableHeader\",\"content\":[]},"
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"hardBreak\"}]}]}");
  }

  @Test
  void every_attribute_value_type_keeps_its_json_type() {
    var attrs =
        new Attributes(
            Map.of(
                "long",
                5,
                "big",
                new BigInteger("99999999999999999999999"),
                "double",
                1.5,
                "decimal",
                new BigDecimal("1.2345678901234567890123456"),
                "bool",
                true,
                "nested",
                Map.of("list", Arrays.asList("x", null, 1))));
    var document = new AdfDocument(1, List.of(new Paragraph(attrs, null, null)));

    assertThat(writer.write(document))
        .contains("\"long\":5")
        .contains("\"big\":99999999999999999999999")
        .contains("\"double\":1.5")
        .contains("\"decimal\":1.2345678901234567890123456")
        .contains("\"bool\":true")
        .contains("\"nested\":{\"list\":[\"x\",null,1]}");
  }

  @Test
  void unknown_nodes_and_marks_write_their_raw_json() {
    var inline = new Text("x", List.of(new UnknownMark("m", "{\"type\":\"m\",\"a\":1}")));
    var document =
        new AdfDocument(
            1,
            List.of(
                new UnknownBlock("b", "{\"type\":\"b\",\"x\":[1]}"),
                new Paragraph(
                    null, List.of(inline, new UnknownInline("i", "{\"type\":\"i\"}")), null)));

    assertThat(writer.write(document))
        .contains("{\"type\":\"b\",\"x\":[1]}")
        .contains("\"marks\":[{\"type\":\"m\",\"a\":1}]")
        .contains("{\"type\":\"i\"}");
  }

  @Test
  void an_unknown_node_built_with_invalid_json_is_rejected() {
    var document = new AdfDocument(1, List.of(new UnknownBlock("b", "not json")));

    assertThatThrownBy(() -> writer.write(document))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not json");
  }
}
