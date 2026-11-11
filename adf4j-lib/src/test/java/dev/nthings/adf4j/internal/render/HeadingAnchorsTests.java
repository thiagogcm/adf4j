package dev.nthings.adf4j.internal.render;

import static dev.nthings.adf4j.testing.AdfSnippets.anchor;
import static dev.nthings.adf4j.testing.AdfSnippets.atom;
import static dev.nthings.adf4j.testing.AdfSnippets.doc;
import static dev.nthings.adf4j.testing.AdfSnippets.heading;
import static dev.nthings.adf4j.testing.AdfSnippets.text;
import static dev.nthings.adf4j.testing.AdfSnippets.toc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.ast.Adf;
import dev.nthings.adf4j.ast.Adf.PanelType;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.analyze.AdfDocumentAnalyzer;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeadingAnchorsTests {

  @Test
  void an_explicit_anchor_is_injected() {
    var document = parse(doc(heading(2, text("Title"), anchor("here"))));

    assertThat(injectAt(document, "/content/0", false)).isEqualTo("here");
  }

  @Test
  void a_toc_referenced_heading_injects_its_slug() {
    var document = parse(doc(toc(), heading(2, text("Getting Started"))));

    assertThat(injectAt(document, "/content/1", false)).isNotBlank();
  }

  @Test
  void a_plain_heading_injects_nothing() {
    var document = parse(doc(heading(2, text("Getting Started"))));

    assertThat(injectAt(document, "/content/0", false)).isNull();
  }

  @Test
  void a_heading_that_rendered_blank_keeps_only_its_explicit_anchor() {
    var anchored = parse(doc(heading(2, text(" "), anchor("here"))));
    var bare = parse(doc(heading(2, text(" "))));

    assertThat(injectAt(anchored, "/content/0", true)).isEqualTo("here");
    assertThat(injectAt(bare, "/content/0", true)).isNull();
  }

  @Test
  void a_textless_status_heading_keeps_its_explicit_anchor() {
    var document = parse(doc(heading(2, anchor("here"), atom("status", "{}"))));
    var outline = outlineOf(document);

    assertThat(outline.headings())
        .extracting("text", "anchor")
        .containsExactly(tuple("[status]", "here"));
    assertThat(injectAt(document, "/content/0", false)).isEqualTo("here");
  }

  @Test
  void a_heading_outside_the_outline_keeps_only_its_explicit_anchor() {
    var anchored = parse(doc(heading(2, text("Title"), anchor("here"))));
    var bare = parse(doc(heading(2, text("Title"))));

    assertThat(injectAt(anchored, "/content/9", false)).isEqualTo("here");
    assertThat(injectAt(bare, "/content/9", false)).isNull();
  }

  @Test
  void the_same_heading_node_at_two_paths_gets_each_occurrence_anchor() {
    var shared = Adf.heading(2, "Shared");
    var document = new AdfDocument(1, List.of(tocMacro(), shared, shared));
    var outline = outlineOf(document);

    assertThat(HeadingAnchors.anchorToInject(outline, shared, "/content/1", false))
        .isEqualTo("shared");
    assertThat(HeadingAnchors.anchorToInject(outline, shared, "/content/2", false))
        .isEqualTo("shared-1");
  }

  @Test
  void a_nested_occurrence_uses_its_filtered_source_path() {
    var shared = Adf.heading(2, "Shared");
    var document =
        new AdfDocument(
            1,
            List.of(tocMacro(), Adf.panel(PanelType.INFO, Adf.paragraph("intro"), shared), shared));
    var outline = outlineOf(document);

    assertThat(HeadingAnchors.anchorToInject(outline, shared, "/content/1/content/1", false))
        .isEqualTo("shared");
    assertThat(HeadingAnchors.anchorToInject(outline, shared, "/content/2", false))
        .isEqualTo("shared-1");
  }

  private static String injectAt(AdfDocument document, String path, boolean renderedBlank) {
    return HeadingAnchors.anchorToInject(
        outlineOf(document), firstHeading(document), path, renderedBlank);
  }

  private static AdfDocument parse(String adfJson) {
    return AdfJson.parse(adfJson).document();
  }

  private static Heading firstHeading(AdfDocument document) {
    return document.content().stream()
        .filter(Heading.class::isInstance)
        .map(Heading.class::cast)
        .findFirst()
        .orElseThrow();
  }

  private static HeadingOutline outlineOf(AdfDocument document) {
    return AdfDocumentAnalyzer.createDefault()
        .facts(document, UnknownNodePolicy.PLACEHOLDER, AttachmentInventory.from(null))
        .outline();
  }

  private static Extension tocMacro() {
    return Adf.extension("com.atlassian.confluence.macro.core", "toc");
  }
}
