package dev.nthings.adf4j.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.AttachmentResolver;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.ExcerptResolver;
import dev.nthings.adf4j.options.ExtensionOutput;
import dev.nthings.adf4j.options.ExtensionRenderer;
import dev.nthings.adf4j.options.MediaResolver;
import dev.nthings.adf4j.options.PageLinkResolver;
import dev.nthings.adf4j.options.PageTreeResolver;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.AdfException;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversionOptionsTests {

  @Test
  void attachments_distinguish_unknown_from_empty_and_are_copied_in_order() {
    var attachments =
        new ArrayList<>(
            List.of(
                new AttachmentReference("b", "b.pdf", null),
                new AttachmentReference("a", null, null)));
    var options = ConversionOptions.defaults().withAttachments(attachments);
    var expected = List.copyOf(attachments);

    attachments.clear();

    assertThat(options.attachments()).containsExactlyElementsOf(expected);
    assertThat(options.withAttachments(List.of()).attachments()).isEmpty();
    assertThat(options.withAttachments(null).attachments()).isNull();
  }

  @Test
  void passing_null_to_a_resolver_setter_clears_it() {
    var options =
        ConversionOptions.defaults()
            .withMediaResolver(attrs -> "x")
            .withAttachmentResolver(reference -> "y")
            .withPageLinkResolver(pageNodeId -> "z");

    assertThat(options.withMediaResolver(null).mediaResolver()).isNull();
    assertThat(options.withAttachmentResolver(null).attachmentResolver()).isNull();
    assertThat(options.withPageLinkResolver(null).pageLinkResolver()).isNull();
  }

  @Test
  void every_wither_sets_its_field_and_keeps_the_others() {
    MediaResolver media = attrs -> "m";
    AttachmentResolver attachment = reference -> "a";
    PageLinkResolver pageLink = pageNodeId -> "p";
    PageTreeResolver pageTree = reference -> List.of();
    ExcerptResolver excerpt = reference -> "e";
    ExtensionRenderer extension = node -> ExtensionOutput.replace("x");
    var attachments = List.of(new AttachmentReference("a", "a.pdf", null));

    var options =
        ConversionOptions.defaults()
            .withUnknownNodePolicy(UnknownNodePolicy.SKIP)
            .withAttachments(attachments)
            .withImageSizeAttributes(true)
            .withTableFallback(TableFallback.HTML)
            .withMediaResolver(media)
            .withHtmlVisualMarks(true)
            .withExtensionRenderers(List.of(extension))
            .withAttachmentResolver(attachment)
            .withPageLinkResolver(pageLink)
            .withPageTreeResolver(pageTree)
            .withExcerptResolver(excerpt)
            .withCollapseHardBreaks(true)
            .withDocumentTitle("My Page")
            .withEscapeParentheses(true);

    assertThat(options.unknownNodePolicy()).isEqualTo(UnknownNodePolicy.SKIP);
    assertThat(options.attachments()).isEqualTo(attachments);
    assertThat(options.imageSizeAttributes()).isTrue();
    assertThat(options.tableFallback()).isEqualTo(TableFallback.HTML);
    assertThat(options.mediaResolver()).isSameAs(media);
    assertThat(options.htmlVisualMarks()).isTrue();
    assertThat(options.extensionRenderers()).containsExactly(extension);
    assertThat(options.attachmentResolver()).isSameAs(attachment);
    assertThat(options.pageLinkResolver()).isSameAs(pageLink);
    assertThat(options.pageTreeResolver()).isSameAs(pageTree);
    assertThat(options.excerptResolver()).isSameAs(excerpt);
    assertThat(options.collapseHardBreaks()).isTrue();
    assertThat(options.documentTitle()).isEqualTo("My Page");
    assertThat(options.escapeParentheses()).isTrue();
  }

  @Test
  void image_size_attributes_suffix_is_opt_in() {
    var json =
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "mediaSingle",
              "attrs": { "layout": "center", "width": 50, "widthType": "percentage" },
              "content": [
                {
                  "type": "media",
                  "attrs": {
                    "type": "external",
                    "url": "https://example.com/diagram.png",
                    "alt": "Sized",
                    "width": 800,
                    "height": 400
                  }
                }
              ]
            }
          ]
        }
        """;

    assertThat(AdfToMarkdown.create().toMarkdown(json).stripTrailing())
        .isEqualTo("![Sized](https://example.com/diagram.png)");

    var withSuffix = ConversionOptions.defaults().withImageSizeAttributes(true);
    assertThat(AdfToMarkdown.with(withSuffix).toMarkdown(json).stripTrailing())
        .isEqualTo("![Sized](https://example.com/diagram.png){width=800 height=400}");
  }

  @Test
  void blank_portable_configuration_is_the_defaults() {
    assertThat(ConversionOptions.fromJson("  ")).isEqualTo(ConversionOptions.defaults());
  }

  @Test
  void invalid_portable_configuration_throws_one_invalid_options_diagnostic() {
    for (var json :
        List.of(
            "{",
            "{\"markdown\":{\"bogus\":true}}",
            "{\"markdown\":{\"tableFallback\":\"nope\"}}")) {
      assertThatExceptionOfType(AdfException.class)
          .isThrownBy(() -> ConversionOptions.fromJson(json))
          .satisfies(
              failure ->
                  assertThat(failure.diagnostics())
                      .extracting(Diagnostic::code)
                      .containsExactly(Diagnostic.Code.INVALID_OPTIONS));
    }
    assertThatExceptionOfType(AdfException.class)
        .isThrownBy(() -> ConversionOptions.fromJson("{\"markdown\":{\"tableFallback\":\"nope\"}}"))
        .withMessage(
            "Invalid configuration: tableFallback must be one of: html, gfm-promote-first-row,"
                + " gfm-empty-header");
  }
}
