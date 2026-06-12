package dev.nthings.adf4j.internal.analyze;

import java.util.ArrayList;
import java.util.List;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.BlockCard;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.ExtensionFrame;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AdfNodeWalkerTests {

  @Test
  void visitor_can_override_only_block_method() {
    NodeVisitor visitor = new NodeVisitor() {
      @Override
      public void visitBlock(AdfBlock block) {
      }
    };
    var document = documentWithParagraph("inline default is harmless");

    assertThatCode(() -> AdfNodeWalker.walk(document, List.of(visitor))).doesNotThrowAnyException();
  }

  @Test
  void visitor_can_override_only_inline_method() {
    NodeVisitor visitor = new NodeVisitor() {
      @Override
      public void visitInline(AdfInline inline) {
      }
    };
    var document = documentWithParagraph("block default is harmless");

    assertThatCode(() -> AdfNodeWalker.walk(document, List.of(visitor))).doesNotThrowAnyException();
  }

  @Test
  void walk_visits_bodied_extension_and_bodied_sync_block_children_in_document_order() {
    var document = new AdfDocument(
        1,
        List.of(
            new BodiedExtension(
                "com.atlassian.confluence.macro.core",
                "tabs-group",
                null,
                MacroParams.empty(),
                null,
                List.of(new ExtensionFrame(List.of(paragraph("extension body"))))),
            new BodiedSyncBlock("resource-1", List.of(paragraph("synced body"))),
            new BlockCard(null)));
    var visited = new ArrayList<String>();

    AdfNodeWalker.walk(document, List.of(new NodeVisitor() {
      @Override
      public void visitBlock(AdfBlock block) {
        visited.add("block:" + block.getClass().getSimpleName());
      }

      @Override
      public void visitInline(AdfInline inline) {
        visited.add("inline:" + inline.getClass().getSimpleName() + ":" + ((Text) inline).text());
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

  private static AdfDocument documentWithParagraph(String text) {
    return new AdfDocument(1, List.of(paragraph(text)));
  }

  private static Paragraph paragraph(String text) {
    return new Paragraph(List.of(new Text(text, List.of())), List.of());
  }
}
