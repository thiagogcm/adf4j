package dev.nthings.adf4j.ast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AstRecordContractTests {

  @Test
  void node_defaults_null_attrs_content_and_marks() {
    var node = new BodiedExtension(null, null, null);

    assertThat(node.attrs()).isEqualTo(Attributes.empty());
    assertThat(node.content()).isEmpty();
    assertThat(node.marks()).isEmpty();
    assertThat(node.macroParams()).isEqualTo(MacroParams.empty());
    assertThat(node.parameters()).isEqualTo(Attributes.empty());
  }

  @Test
  void container_defensively_copies_content_and_marks() {
    var content = new ArrayList<AdfBlock>();
    content.add(new Paragraph(null, List.of(new Text("kept", List.of())), null));
    var marks = new ArrayList<AdfMark>();
    marks.add(new Strong(null));

    var node = new BodiedSyncBlock(null, content, marks);
    content.clear();
    marks.clear();

    assertThat(node.content()).hasSize(1);
    assertThat(node.marks()).hasSize(1);
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> node.content().clear());
  }

  @Test
  void card_nodes_expose_empty_card_attrs_for_null_attrs() {
    var emptyAttrs = new CardAttrs(null, null, null, null, Attributes.empty());

    assertThat(new InlineCard(null, null).cardAttrs()).isEqualTo(emptyAttrs);
    assertThat(new BlockCard(null, null).cardAttrs()).isEqualTo(emptyAttrs);
    assertThat(new EmbedCard(null, null).cardAttrs()).isEqualTo(emptyAttrs);
  }

  @Test
  void each_record_reports_its_json_type() {
    assertThat(new TableCell(false, null, null, null).type()).isEqualTo("tableCell");
    assertThat(new TableCell(true, null, null, null).type()).isEqualTo("tableHeader");
    assertThat(new SubSup(null).type()).isEqualTo("subsup");
    assertThat(new AdfDocument(1, null).type()).isEqualTo("doc");
    assertThat(new UnknownBlock("mystery", "{}").type()).isEqualTo("mystery");
  }

  @Test
  void accessors_read_attrs_leniently_without_changing_them() {
    var attrs = new Attributes(Map.of("level", "9", "colspan", 0));

    var heading = new Heading(attrs, null, null);
    var cell = new TableCell(false, attrs, null, null);

    assertThat(heading.level()).isEqualTo(9);
    assertThat(cell.colspan()).isEqualTo(1);
    assertThat(heading.attrs()).isSameAs(attrs);
  }

  @Test
  void filtered_accessors_hide_off_schema_children_that_content_keeps() {
    var stray = new UnknownBlock("mystery", "{}");
    var item = new ListItem(null, null, null);

    var list = new BulletList(null, List.of(stray, item), null);

    assertThat(list.content()).containsExactly(stray, item);
    assertThat(list.items()).containsExactly(item);
  }
}
