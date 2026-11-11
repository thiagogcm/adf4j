package dev.nthings.adf4j.oracle;

import static dev.nthings.adf4j.testing.AdfSnippets.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.ExtensionOutput;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// Whatever a heading renders must be in the outline, and every anchor must be unique. Both sides
/// are checked end to end: the Markdown body and the metadata outline.
class HeadingOutlineOracleTests {

  private static final AdfToMarkdown CONVERTER = AdfToMarkdown.create();

  private static List<String> anchorIds(String markdown) {
    return Jsoup.parse(CommonMarkTestSupport.toHtml(markdown)).select("a[id]").eachAttr("id");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "mention  | {\"id\":\"abc\"}                  | ## @abc              | @abc",
        "mention  | {\"localId\":\"loc\"}             | ## @loc              | @loc",
        "mention  | {}                                | ## @unknown          | @unknown",
        "mention  | {\"text\":\"@Bob\"}               | ## @Bob              | @Bob",
        "status   | {}                                | ## \\[status\\]      | [status]",
        "status   | {\"text\":\" \"}                  | ## \\[status\\]      | [status]",
        "status   | {\"text\":\"DONE\"}               | ## \\[DONE\\]        | [DONE]",
        "emoji    | {\"shortName\":\":smile:\"}       | ## :smile:           | :smile:",
        "emoji    | {\"text\":\"X\",\"shortName\":\":s:\"} | ## X             | X",
      })
  void a_textless_inline_renders_and_lists_the_same_label(
      String type, String attrs, String expectedLine, String expectedText) {
    var result = CONVERTER.convert(doc(heading(2, atom(type, attrs))));

    assertThat(result.body()).isEqualTo(expectedLine);
    assertThat(result.metadata().outline()).extracting("text").containsExactly(expectedText);
  }

  @Test
  void an_inline_extension_heading_is_in_the_outline_with_its_fallback_text() {
    var extension =
        atom(
            "inlineExtension",
            "{\"extensionType\":\"x\",\"extensionKey\":\"y\",\"text\":\"Fallback\"}");

    var result = CONVERTER.convert(doc(heading(2, extension)));

    assertThat(result.body()).isEqualTo("## Fallback");
    assertThat(result.metadata().outline()).extracting("text").containsExactly("Fallback");
  }

  @Test
  void a_placeholder_inline_extension_heading_is_in_the_outline_with_its_label() {
    var extension = atom("inlineExtension", "{\"extensionType\":\"x\",\"extensionKey\":\"y\"}");

    var result = CONVERTER.convert(doc(heading(2, extension)));

    assertThat(result.body()).isEqualTo("## \\[Extension: x/y\\]");
    assertThat(result.metadata().outline()).extracting("text").containsExactly("[Extension: x/y]");
  }

  @Test
  void a_media_image_macro_heading_is_in_the_outline_with_its_alt() {
    var image =
        atom(
            "inlineExtension",
            "{\"extensionType\":\"com.atlassian.confluence.migration\","
                + "\"extensionKey\":\"inline-media-image\","
                + "\"parameters\":{\"id\":\"f\",\"alt\":\"Logo\"}}");

    assertThat(CONVERTER.convert(doc(heading(2, image))).metadata().outline())
        .extracting("text")
        .containsExactly("Logo");
  }

  @Test
  void a_textless_card_heading_is_in_the_outline_with_its_label() {
    var result = CONVERTER.convert(doc(heading(2, atom("inlineCard", "{}"))));

    assertThat(result.body()).isEqualTo("## \\[Inline card\\]");
    assertThat(result.metadata().outline()).extracting("text").containsExactly("[Inline card]");
  }

  @Test
  void an_unknown_inline_heading_follows_the_unknown_node_policy_in_the_outline() {
    var document = doc(heading(2, atom("futureInline", "{}")));

    var placeholder = CONVERTER.convert(document);
    assertThat(placeholder.body()).isEqualTo("## \\[Unsupported inline: futureInline\\]");
    assertThat(placeholder.metadata().outline())
        .extracting("text")
        .containsExactly("[Unsupported inline: futureInline]");

    var raw =
        AdfToMarkdown.with(
                ConversionOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.PRESERVE_RAW))
            .convert(document);
    assertThat(raw.metadata().outline()).hasSize(1);

    var skip =
        AdfToMarkdown.with(
                ConversionOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.SKIP))
            .convert(document);
    assertThat(skip.body()).isEmpty();
    assertThat(skip.metadata().outline()).isEmpty();
  }

  @Test
  void a_link_over_blank_text_is_in_the_outline_with_the_href_it_shows() {
    var result = CONVERTER.convert(doc(heading(2, text(" ", link("https://e.com")))));

    assertThat(result.body()).isEqualTo("## [https://e.com](https://e.com)");
    assertThat(result.metadata().outline()).extracting("text").containsExactly("https://e.com");
  }

  @Test
  void a_textless_status_heading_keeps_its_explicit_anchor() {
    var result = CONVERTER.convert(doc(heading(2, anchor("here"), atom("status", "{}"))));

    assertThat(result.body()).isEqualTo("<a id=\"here\"></a>\n## \\[status\\]");
    assertThat(result.metadata().outline())
        .extracting("text", "anchor")
        .containsExactly(tuple("[status]", "here"));
  }

  @Test
  void a_custom_rendered_heading_keeps_its_explicit_anchor_and_a_toc_entry() {
    var converter =
        AdfToMarkdown.with(
            ConversionOptions.defaults()
                .withExtensionRenderers(
                    List.of(
                        extension ->
                            "y".equals(extension.extensionKey())
                                ? ExtensionOutput.replace("Custom")
                                : ExtensionOutput.defer())));
    var extension = atom("inlineExtension", "{\"extensionType\":\"x\",\"extensionKey\":\"y\"}");

    var result = converter.convert(doc(toc(), heading(2, anchor("here"), extension)));

    assertThat(result.body()).endsWith("<a id=\"here\"></a>\n## Custom");
    assertThat(result.metadata().outline()).extracting("anchor").containsExactly("here");
  }

  @Test
  void a_line_break_in_heading_text_is_a_space_in_the_outline_and_its_slug() {
    var result = CONVERTER.convert(doc(toc(), heading(2, text("one\ntwo"))));

    assertThat(result.metadata().outline())
        .extracting("text", "anchor")
        .containsExactly(tuple("one two", "one-two"));
    assertThat(result.body()).startsWith("- [one two](#one-two)\n");
  }

  @Test
  void an_explicit_anchor_wins_a_slug_collision_even_when_declared_later() {
    var result =
        CONVERTER.convert(
            doc(toc(), heading(2, text("Foo")), heading(2, anchor("foo"), text("Other"))));

    assertThat(anchorIds(result.body())).containsExactly("foo-1", "foo");
    assertThat(result.metadata().outline())
        .extracting("text", "anchor")
        .containsExactly(tuple("Foo", "foo-1"), tuple("Other", "foo"));
    assertThat(result.body()).startsWith("- [Foo](#foo-1)\n- [Other](#foo)\n");
  }

  @Test
  void an_explicit_anchor_is_also_reserved_when_declared_first() {
    var result =
        CONVERTER.convert(
            doc(toc(), heading(2, anchor("foo"), text("Other")), heading(2, text("Foo"))));

    assertThat(anchorIds(result.body())).containsExactly("foo", "foo-1");
  }

  @Test
  void a_generated_suffix_steps_over_an_explicit_anchor() {
    var result =
        CONVERTER.convert(
            doc(
                toc(),
                heading(2, anchor("foo-1"), text("Other")),
                heading(2, text("Foo")),
                heading(2, text("Foo"))));

    assertThat(anchorIds(result.body())).containsExactly("foo-1", "foo", "foo-2");
  }

  @Test
  void a_generated_suffix_steps_over_a_slug_generated_from_other_text() {
    var result =
        CONVERTER.convert(
            doc(
                toc(),
                heading(2, text("Foo 1")),
                heading(2, text("Foo")),
                heading(2, text("Foo"))));

    assertThat(anchorIds(result.body())).containsExactly("foo-1", "foo", "foo-2");
  }

  @Test
  void an_anchor_outside_any_heading_is_reserved_too() {
    var result = CONVERTER.convert(doc(toc(), paragraph(anchor("foo")), heading(2, text("Foo"))));

    assertThat(anchorIds(result.body())).containsExactly("foo", "foo-1");
  }

  @Test
  void an_anchor_on_a_heading_that_renders_blank_is_reserved_too() {
    var result = CONVERTER.convert(doc(toc(), heading(2, anchor("foo")), heading(2, text("Foo"))));

    assertThat(anchorIds(result.body())).containsExactly("foo", "foo-1");
  }
}
