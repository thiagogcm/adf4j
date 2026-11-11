package dev.nthings.adf4j.internal.analyze;

import static dev.nthings.adf4j.testing.AdfNodes.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.BlockCard;
import dev.nthings.adf4j.ast.Text;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdfNodeWalkerTests {

  @Test
  void walk_visits_bodied_extension_and_bodied_sync_block_children_in_document_order() {
    var document =
        new AdfDocument(
            1,
            List.of(
                bodiedExtension(
                    "com.atlassian.confluence.macro.core",
                    "tabs-group",
                    List.of(extensionFrame(List.of(paragraph("extension body"))))),
                bodiedSyncBlock("resource-1", List.of(paragraph("synced body"))),
                new BlockCard(Attributes.empty(), List.of())));
    var visited = new ArrayList<String>();

    AdfNodeWalker.walk(
        document,
        List.of(
            new NodeVisitor() {
              @Override
              public void visitBlock(AdfBlock block, String path) {
                visited.add("block:" + block.getClass().getSimpleName());
              }

              @Override
              public void visitInline(AdfInline inline, String path) {
                visited.add(
                    "inline:" + inline.getClass().getSimpleName() + ":" + ((Text) inline).text());
              }
            }));

    assertThat(visited)
        .containsExactly(
            "block:BodiedExtension",
            "block:ExtensionFrame",
            "block:Paragraph",
            "inline:Text:extension body",
            "block:BodiedSyncBlock",
            "block:Paragraph",
            "inline:Text:synced body",
            "block:BlockCard");
  }
}
