package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import dev.nthings.adf4j.HeadingReference;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.confluence.ExcerptKey;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MacroContextTests {

  @Test
  void from_filters_null_keys_and_defensively_copies_collections() {
    var key = new ExcerptKey("Fixture", "summary");
    AdfBlock helloParagraph = new Paragraph(List.of(new Text("hello", List.of())), List.of());
    AdfBlock ignoredParagraph = new Paragraph(List.of(new Text("ignored", List.of())), List.of());
    AdfBlock mutatedParagraph = new Paragraph(List.of(new Text("mutated", List.of())), List.of());
    var excerptBlocks = new ArrayList<AdfBlock>(List.of(helloParagraph));
    var headings = new ArrayList<HeadingReference>(
        List.of(new HeadingReference(2, "Overview", "overview")));
    var excerpts = new LinkedHashMap<ExcerptKey, List<AdfBlock>>();
    excerpts.put(null, List.of(ignoredParagraph));
    excerpts.put(key, excerptBlocks);

    var context = MacroContext.from(excerpts, headings);

    excerptBlocks.add(mutatedParagraph);
    headings.add(new HeadingReference(3, "Later", "later"));

    assertThat(context.excerpts()).containsOnlyKeys(key);
    assertThat(context.excerpts().get(key)).hasSize(1);
    assertThat(context.headings())
        .containsExactly(new HeadingReference(2, "Overview", "overview"));
    assertThatThrownBy(() -> context.excerpts().put(new ExcerptKey("Other", null), List.of()))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> context.headings().add(new HeadingReference(3, "Later", "later")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
