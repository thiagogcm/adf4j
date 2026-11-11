package dev.nthings.adf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversionOptionsTests {

  @Test
  void defaults_use_placeholder_policy_and_an_unknown_attachment_inventory() {
    var options = ConversionOptions.defaults();

    assertThat(options.unknownNodePolicy()).isEqualTo(UnknownNodePolicy.PLACEHOLDER);
    assertThat(options.attachments()).isNull();
    assertThat(options.tableFallback()).isEqualTo(TableFallback.GFM_EMPTY_HEADER);
    assertThat(options.mediaResolver()).isNull();
    assertThat(options.extensionRenderers()).isEmpty();
    assertThat(options.attachmentResolver()).isNull();
    assertThat(options.pageLinkResolver()).isNull();
    assertThat(options.pageTreeResolver()).isNull();
    assertThat(options.excerptResolver()).isNull();
    assertThat(options.collapseHardBreaks()).isFalse();
    assertThat(options.documentTitle()).isNull();
    assertThat(options.escapeParentheses()).isFalse();
  }

  @Test
  void required_values_reject_null() {
    var options = ConversionOptions.defaults();

    assertThatNullPointerException().isThrownBy(() -> options.withUnknownNodePolicy(null));
    assertThatNullPointerException().isThrownBy(() -> options.withTableFallback(null));
    assertThatNullPointerException().isThrownBy(() -> options.withExtensionRenderers(null));
  }

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
  void table_fallback_and_media_resolver_default_and_carry_through_other_withers() {
    var options = ConversionOptions.defaults();
    assertThat(options.tableFallback()).isEqualTo(TableFallback.GFM_EMPTY_HEADER);
    assertThat(options.mediaResolver()).isNull();

    var fallback = options.withTableFallback(TableFallback.HTML);
    assertThat(fallback.tableFallback()).isEqualTo(TableFallback.HTML);
    assertThat(fallback.withUnknownNodePolicy(UnknownNodePolicy.SKIP).tableFallback())
        .isEqualTo(TableFallback.HTML);
    assertThat(fallback.withImageSizeAttributes(true).tableFallback())
        .isEqualTo(TableFallback.HTML);

    var resolver = options.withMediaResolver(attrs -> "https://cdn.example.com/" + attrs.id());
    assertThat(resolver.mediaResolver()).isNotNull();
    assertThat(resolver.withUnknownNodePolicy(UnknownNodePolicy.SKIP).mediaResolver())
        .isSameAs(resolver.mediaResolver());
    assertThat(resolver.withTableFallback(TableFallback.GFM_EMPTY_HEADER).mediaResolver())
        .isSameAs(resolver.mediaResolver());
  }

  @Test
  void image_size_attributes_default_off_and_carry_through_other_withers() {
    var options = ConversionOptions.defaults();
    assertThat(options.imageSizeAttributes()).isFalse();

    var enabled = options.withImageSizeAttributes(true);
    assertThat(enabled.imageSizeAttributes()).isTrue();
    assertThat(enabled.withUnknownNodePolicy(UnknownNodePolicy.SKIP).imageSizeAttributes())
        .isTrue();
    assertThat(enabled.withAttachments(List.of()).imageSizeAttributes()).isTrue();
  }

  @Test
  void attachment_and_page_link_resolvers_default_null_and_carry_through_other_withers() {
    var options = ConversionOptions.defaults();
    assertThat(options.attachmentResolver()).isNull();
    assertThat(options.pageLinkResolver()).isNull();

    var attachment = options.withAttachmentResolver(reference -> "files/" + reference.fileId());
    assertThat(attachment.attachmentResolver()).isNotNull();
    assertThat(attachment.withUnknownNodePolicy(UnknownNodePolicy.SKIP).attachmentResolver())
        .isSameAs(attachment.attachmentResolver());

    var pageLink = options.withPageLinkResolver(pageNodeId -> "pages/" + pageNodeId);
    assertThat(pageLink.pageLinkResolver()).isNotNull();
    assertThat(pageLink.withTableFallback(TableFallback.HTML).pageLinkResolver())
        .isSameAs(pageLink.pageLinkResolver());
    // The two resolvers are independent and coexist.
    var both = attachment.withPageLinkResolver(pageNodeId -> "pages/" + pageNodeId);
    assertThat(both.attachmentResolver()).isSameAs(attachment.attachmentResolver());
    assertThat(both.pageLinkResolver()).isNotNull();
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
  void a_null_extension_renderer_element_is_rejected_with_a_named_message() {
    var renderers = Arrays.<ExtensionRenderer>asList(extension -> ExtensionOutput.defer(), null);

    assertThatNullPointerException()
        .isThrownBy(() -> ConversionOptions.defaults().withExtensionRenderers(renderers));
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
  void escape_parentheses_default_off_and_carry_through_other_withers() {
    var options = ConversionOptions.defaults();
    assertThat(options.escapeParentheses()).isFalse();

    var enabled = options.withEscapeParentheses(true);
    assertThat(enabled.escapeParentheses()).isTrue();
    assertThat(enabled.withUnknownNodePolicy(UnknownNodePolicy.SKIP).escapeParentheses()).isTrue();
    assertThat(enabled.withDocumentTitle("My Page").escapeParentheses()).isTrue();
    assertThat(enabled.withEscapeParentheses(false).escapeParentheses()).isFalse();
  }

  @Test
  void html_visual_marks_default_off_and_carry_through_other_withers() {
    var options = ConversionOptions.defaults();
    assertThat(options.htmlVisualMarks()).isFalse();

    var enabled = options.withHtmlVisualMarks(true);
    assertThat(enabled.htmlVisualMarks()).isTrue();
    assertThat(enabled.withUnknownNodePolicy(UnknownNodePolicy.SKIP).htmlVisualMarks()).isTrue();
    assertThat(enabled.withTableFallback(TableFallback.HTML).htmlVisualMarks()).isTrue();
  }

  @Test
  void collapse_hard_breaks_default_off_and_carry_through_other_withers() {
    var options = ConversionOptions.defaults();
    assertThat(options.collapseHardBreaks()).isFalse();

    var enabled = options.withCollapseHardBreaks(true);
    assertThat(enabled.collapseHardBreaks()).isTrue();
    assertThat(enabled.withUnknownNodePolicy(UnknownNodePolicy.SKIP).collapseHardBreaks()).isTrue();
    assertThat(enabled.withTableFallback(TableFallback.HTML).collapseHardBreaks()).isTrue();
  }

  @Test
  void document_title_default_null_and_carry_through_other_withers() {
    var options = ConversionOptions.defaults();
    assertThat(options.documentTitle()).isNull();

    var titled = options.withDocumentTitle("My Page");
    assertThat(titled.documentTitle()).isEqualTo("My Page");
    assertThat(titled.withUnknownNodePolicy(UnknownNodePolicy.SKIP).documentTitle())
        .isEqualTo("My Page");
    assertThat(titled.withCollapseHardBreaks(true).documentTitle()).isEqualTo("My Page");
    assertThat(titled.withDocumentTitle(null).documentTitle()).isNull();
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
}
