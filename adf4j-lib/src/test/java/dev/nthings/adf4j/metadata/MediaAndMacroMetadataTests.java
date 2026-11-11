package dev.nthings.adf4j.metadata;

import static dev.nthings.adf4j.testing.AdfSnippets.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.options.ConversionOptions;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

/// Metadata reports what renders: references in a subtree the renderer drops, or behind a
/// placeholder macro, are not references.
class MediaAndMacroMetadataTests {

  private static final AdfToMarkdown CONVERTER = AdfToMarkdown.create();

  private static ContentMetadata metadata(String adf) {
    return CONVERTER.convert(adf).metadata();
  }

  private static String external(String url) {
    return atom("media", "{\"type\":\"external\",\"url\":" + quote(url) + "}");
  }

  private static String mediaSingle(String marks, String... content) {
    return node("mediaSingle", "{}", content)
        .replace("\"content\"", "\"marks\":[" + marks + "],\"content\"");
  }

  @Test
  void a_link_mark_on_a_media_single_is_a_reference() {
    var result =
        CONVERTER.convert(
            doc(mediaSingle(link("https://l.example"), external("https://i.example/a.png"))));

    assertThat(result.body()).isEqualTo("[![media](https://i.example/a.png)](https://l.example)");
    assertThat(result.metadata().externalUrls())
        .containsExactly("https://l.example", "https://i.example/a.png");
  }

  @Test
  void a_page_link_mark_on_a_media_single_is_a_page_reference() {
    var page = "https://x.atlassian.net/wiki/spaces/S/pages/123/T";

    assertThat(metadata(doc(mediaSingle(link(page), external("https://i/a.png")))).pageIds())
        .containsExactly("123");
  }

  @Test
  void a_link_mark_on_a_media_single_without_media_renders_nothing_so_it_is_no_reference() {
    var document = doc(mediaSingle(link("https://l.example"), paragraph(text("stray"))));

    assertThat(CONVERTER.toMarkdown(document)).isEmpty();
    assertThat(metadata(document).externalUrls()).isEmpty();
  }

  @Test
  void only_the_link_that_renders_is_a_reference() {
    var result =
        CONVERTER.convert(
            doc(paragraph(text("x", link("https://first.example"), link("https://last.example")))));

    assertThat(result.body()).isEqualTo("[x](https://last.example)");
    assertThat(result.metadata().externalUrls()).containsExactly("https://last.example");
  }

  @Test
  void a_trailing_link_without_an_href_unlinks_the_text_and_references_nothing() {
    var result =
        CONVERTER.convert(
            doc(paragraph(text("x", link("https://first.example"), mark("link", "{}")))));

    assertThat(result.body()).isEqualTo("x");
    assertThat(result.metadata().externalUrls()).isEmpty();
  }

  @Test
  void stray_children_of_media_wrappers_feed_no_metadata() {
    var stray = paragraph(text("s", link("http://ms")), atom("mention", "{\"id\":\"m\"}"));

    var single = doc(mediaSingle("", stray));
    var group = doc(node("mediaGroup", "{}", stray));

    assertThat(CONVERTER.toMarkdown(single)).isEmpty();
    assertThat(metadata(single)).isEqualTo(ContentMetadata.empty());
    assertThat(CONVERTER.toMarkdown(group)).isEmpty();
    assertThat(metadata(group)).isEqualTo(ContentMetadata.empty());
  }

  @Test
  void a_media_group_still_feeds_its_items_and_a_media_single_its_caption() {
    var caption = node("caption", "{}", text("c", link("https://caption.example")));

    assertThat(
            metadata(
                    doc(
                        node("mediaGroup", "{}", external("https://a/1.png")),
                        mediaSingle("", external("https://b/2.png"), caption)))
                .externalUrls())
        .containsExactly("https://a/1.png", "https://b/2.png", "https://caption.example");
  }

  @Test
  void the_typed_accessors_hide_stray_children() {
    var document =
        AdfJson.parse(
                doc(
                    mediaSingle(
                        "",
                        paragraph(),
                        external("https://a"),
                        node("caption", "{}", text("c")),
                        external("https://b")),
                    node("mediaGroup", "{}", paragraph(), external("https://c"))))
            .document();

    var single = (MediaSingle) document.content().get(0);
    var group = (MediaGroup) document.content().get(1);

    assertThat(single.content()).hasSize(4);
    assertThat(single.media()).extracting(Media::url).containsExactly("https://a", "https://b");
    assertThat(single.captions()).hasSize(1);
    assertThat(group.items()).extracting(Media::url).containsExactly("https://c");
    assertThat(new MediaSingle(Attributes.empty(), List.of(), List.of()).captions()).isEmpty();
  }

  @Test
  void a_second_caption_still_renders_and_feeds_metadata() {
    var result =
        CONVERTER.convert(
            doc(
                mediaSingle(
                    "",
                    external("https://a"),
                    node("caption", "{}", text("first")),
                    node("caption", "{}", text("second", link("https://caption.example"))))));

    assertThat(result.body()).contains("first").contains("second");
    assertThat(result.metadata().externalUrls()).contains("https://caption.example");
  }

  @Test
  void a_media_source_type_is_not_a_mime_type() {
    var file = atom("media", "{\"type\":\"file\",\"id\":\"f1\",\"url\":\"https://i/1\"}");
    var named =
        atom(
            "media",
            "{\"type\":\"external\",\"id\":\"f2\",\"__fileName\":\"a.png\",\"url\":\"https://i/2\"}");

    assertThat(metadata(doc(mediaSingle("", file), mediaSingle("", named))).attachmentRefs())
        .containsExactly(
            new AttachmentReference("f1", null, null),
            new AttachmentReference("f2", "a.png", "image/png"));
  }

  @Test
  void a_local_id_is_not_a_file_id() {
    var media = atom("media", "{\"type\":\"file\",\"localId\":\"editor-1\"}");

    assertThat(metadata(doc(mediaSingle("", media))).attachmentRefs()).isEmpty();
  }

  @Test
  void a_link_on_several_media_wraps_each_image_so_the_links_survive() {
    var markdown =
        CONVERTER.toMarkdown(
            doc(
                mediaSingle(
                    link("https://l"), external("https://i/a.png"), external("https://i/b.png"))));

    assertThat(markdown)
        .isEqualTo(
            "[![media](https://i/a.png)](https://l)\n\n[![media](https://i/b.png)](https://l)");
  }

  @Test
  void bodied_macros_that_render_a_placeholder_record_nothing() {
    var inventory =
        ConversionOptions.defaults()
            .withAttachments(List.of(new AttachmentReference("1", "a.pdf", "application/pdf")));
    var body = paragraph(text("b"));
    var pdf = "{\"name\":{\"value\":\"a.pdf\"}}";
    var page = "{\"\":{\"value\":\"Page\"}}";

    for (var nodeType : List.of("bodiedExtension", "multiBodiedExtension")) {
      var document =
          doc(
              macro(nodeType, "pagetree", "{}", body),
              macro(nodeType, "children", "{}", body),
              macro(nodeType, "excerpt-include", page, body),
              macro(nodeType, "attachments", "{}", body),
              macro(nodeType, "viewpdf", pdf, body));

      var result = AdfToMarkdown.with(inventory).convert(document);

      assertThat(result.body()).contains("Extension");
      assertThat(result.metadata().pageTreeRefs()).isEmpty();
      assertThat(result.metadata().excerptRefs()).isEmpty();
      assertThat(result.metadata().attachmentRefs()).isEmpty();
    }
  }

  @Test
  void the_same_macros_without_a_body_keep_recording() {
    var inventory =
        ConversionOptions.defaults()
            .withAttachments(List.of(new AttachmentReference("1", "a.pdf", "application/pdf")));
    var metadata =
        AdfToMarkdown.with(inventory)
            .convert(
                doc(
                    macro("extension", "pagetree", "{}"),
                    macro("extension", "excerpt-include", "{\"\":{\"value\":\"Page\"}}"),
                    macro("extension", "attachments", "{}")))
            .metadata();

    assertThat(metadata.pageTreeRefs()).hasSize(1);
    assertThat(metadata.excerptRefs()).hasSize(1);
    assertThat(metadata.attachmentRefs()).hasSize(1);
  }

  @Test
  void a_bodied_macro_body_still_feeds_metadata_and_excerpts_stay_defined() {
    var result =
        CONVERTER.convert(
            doc(
                macro(
                    "bodiedExtension",
                    "pagetree",
                    "{}",
                    paragraph(text("x", link("https://in.body")))),
                macro(
                    "bodiedExtension",
                    "excerpt",
                    "{\"name\":{\"value\":\"e\"}}",
                    paragraph(text("y")))));

    assertThat(result.metadata().externalUrls()).containsExactly("https://in.body");
    assertThat(result.metadata().excerpts()).hasSize(1);
  }

  @Test
  void parsed_media_links_are_real_links_in_commonmark() {
    var html =
        org.commonmark.renderer.html.HtmlRenderer.builder()
            .build()
            .render(
                org.commonmark.parser.Parser.builder()
                    .build()
                    .parse(
                        CONVERTER.toMarkdown(
                            doc(
                                mediaSingle(
                                    link("https://l"),
                                    external("https://i/a.png"),
                                    external("https://i/b.png"))))));

    assertThat(Jsoup.parse(html).select("a[href=https://l] img")).hasSize(2);
  }
}
