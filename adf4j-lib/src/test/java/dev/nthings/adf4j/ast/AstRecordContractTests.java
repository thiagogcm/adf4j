package dev.nthings.adf4j.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AstRecordContractTests {

  @Test
  void bodied_extension_defaults_null_macro_params_parameters_and_content() {
    var node = new BodiedExtension("com.example", "macro", null, null, null, null);

    assertThat(node.macroParams()).isEqualTo(MacroParams.empty());
    assertThat(node.parameters()).isEqualTo(Attributes.empty());
    assertThat(node.content()).isEmpty();
  }

  @Test
  void bodied_extension_defensively_copies_content() {
    var content = new ArrayList<AdfBlock>();
    content.add(new Paragraph(List.of(new Text("kept", List.of())), List.of()));

    var node = new BodiedExtension(
        "com.example", "macro", "Body", new MacroParams(Map.of("k", "v")),
        new Attributes(Map.of("extensionTitle", "Body")), content);
    content.clear();

    assertThat(node.content()).hasSize(1);
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> node.content().clear());
  }

  @Test
  void bodied_sync_block_defaults_null_content() {
    var node = new BodiedSyncBlock("resource-1", null);

    assertThat(node.content()).isEmpty();
  }

  @Test
  void bodied_sync_block_defensively_copies_content() {
    var content = new ArrayList<AdfBlock>();
    content.add(new Paragraph(List.of(new Text("synced", List.of())), List.of()));

    var node = new BodiedSyncBlock("resource-1", content);
    content.clear();

    assertThat(node.content()).hasSize(1);
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> node.content().clear());
  }

  @Test
  void card_nodes_default_null_attrs_to_empty_card_attrs() {
    var emptyAttrs = new CardAttrs(null, null, null, null, Attributes.empty());

    assertThat(new InlineCard(null).attrs()).isEqualTo(emptyAttrs);
    assertThat(new BlockCard(null).attrs()).isEqualTo(emptyAttrs);
    assertThat(new EmbedCard(null).attrs()).isEqualTo(emptyAttrs);
  }
}
