package dev.nthings.adf4j.reporting;

import static dev.nthings.adf4j.ast.Adf.bulletList;
import static dev.nthings.adf4j.ast.Adf.doc;
import static dev.nthings.adf4j.ast.Adf.listItem;
import static dev.nthings.adf4j.ast.Adf.paragraph;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.ast.TextColor;
import dev.nthings.adf4j.ast.UnknownBlock;
import dev.nthings.adf4j.ast.UnknownMark;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.result.ConversionEffect;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// Reports locate content by position, so one AST instance reused in several places is reported at
/// each of them.
class SourcePathTests {

  private static final UnknownBlock UNKNOWN = new UnknownBlock("mystery", "{\"type\":\"mystery\"}");
  private static final Text MARKED =
      new Text("hi", List.of(new UnknownMark("futureMark", "{\"type\":\"futureMark\"}")));

  @Test
  void a_reused_unknown_block_is_reported_at_every_position() {
    var document = new AdfDocument(1, List.of(UNKNOWN, UNKNOWN));

    var result = AdfToMarkdown.create().convert(document);

    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            diagnostic -> {
              assertThat(diagnostic.code()).isEqualTo(Diagnostic.Code.UNKNOWN_NODE_PLACEHOLDER);
              assertThat(diagnostic.paths()).containsExactly("/content/0", "/content/1");
            });
    assertThat(result.effects())
        .extracting(ConversionEffect::path)
        .containsExactly("/content/0", "/content/1");
    assertThat(AdfToMarkdown.create().analyze(document).diagnostics().getFirst().paths())
        .containsExactly("/content/0", "/content/1");
  }

  @Test
  void a_reused_paragraph_inside_list_items_keeps_each_items_mark_path() {
    var paragraph = paragraph(MARKED);
    var document = doc(bulletList(listItem(paragraph), listItem(paragraph)));

    var result = AdfToMarkdown.create().convert(document);

    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            diagnostic ->
                assertThat(diagnostic.paths())
                    .containsExactly(
                        "/content/0/content/0/content/0/content/0/marks/0",
                        "/content/0/content/1/content/0/content/0/marks/0"));
  }

  @Test
  void an_unsafe_visual_value_is_reported_as_omitted_even_when_visual_marks_are_on() {
    var color = new TextColor(new Attributes(Map.of("color", "red;}")));
    var document = doc(paragraph(new Text("hi", List.of(color))));

    var result =
        AdfToMarkdown.with(ConversionOptions.defaults().withHtmlVisualMarks(true))
            .convert(document);

    assertThat(result.body()).isEqualTo("hi");
    assertThat(result.effects())
        .singleElement()
        .satisfies(
            effect -> {
              assertThat(effect.kind()).isEqualTo(ConversionEffect.Kind.STYLE_OMITTED);
              assertThat(effect.path()).isEqualTo("/content/0/content/0/marks/0");
            });
  }
}
