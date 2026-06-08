package dev.nthings.adf4j;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.options.AttachmentResolver;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.MediaResolver;
import dev.nthings.adf4j.options.PageLinkResolver;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownOptionsTests {

  @Test
  void defaults_use_placeholder_policy_and_empty_render_context() {
    var options = MarkdownOptions.defaults();

    assertThat(options.unknownNodePolicy()).isEqualTo(UnknownNodePolicy.PLACEHOLDER);
    assertThat(options.context()).isEqualTo(ConfluenceRenderContext.empty());
  }

  @Test
  void constructor_and_copy_methods_normalize_null_policy_and_context() {
    var options =
        new MarkdownOptions(
            null, null, false, null, null, false, null, null, null, null, false, null, false);

    assertThat(options.unknownNodePolicy()).isEqualTo(UnknownNodePolicy.PLACEHOLDER);
    assertThat(options.context()).isEqualTo(ConfluenceRenderContext.empty());
    assertThat(options.tableFallback()).isEqualTo(TableFallback.GFM_PROMOTE_FIRST_ROW);
    assertThat(options.mediaResolver()).isNull();
    assertThat(options.extensionRenderers()).isEmpty();
    assertThat(options.attachmentResolver()).isNull();
    assertThat(options.pageLinkResolver()).isNull();
    assertThat(options.pageTreeResolver()).isNull();
    assertThat(options.collapseHardBreaks()).isFalse();
    assertThat(options.documentTitle()).isNull();
    assertThat(options.escapeParentheses()).isFalse();
    assertThat(options.withUnknownNodePolicy(UnknownNodePolicy.SKIP).unknownNodePolicy())
        .isEqualTo(UnknownNodePolicy.SKIP);
    assertThat(options.withContext(null).context()).isEqualTo(ConfluenceRenderContext.empty());
  }

  @Test
  void table_fallback_and_media_resolver_default_and_carry_through_other_withers() {
    var options = MarkdownOptions.defaults();
    assertThat(options.tableFallback()).isEqualTo(TableFallback.GFM_PROMOTE_FIRST_ROW);
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
    var options = MarkdownOptions.defaults();
    assertThat(options.imageSizeAttributes()).isFalse();

    var enabled = options.withImageSizeAttributes(true);
    assertThat(enabled.imageSizeAttributes()).isTrue();
    assertThat(enabled.withUnknownNodePolicy(UnknownNodePolicy.SKIP).imageSizeAttributes()).isTrue();
    assertThat(enabled.withContext(ConfluenceRenderContext.empty()).imageSizeAttributes()).isTrue();
  }

  @Test
  void attachment_and_page_link_resolvers_default_null_and_carry_through_other_withers() {
    var options = MarkdownOptions.defaults();
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
    var options = MarkdownOptions.defaults()
        .withMediaResolver(attrs -> "x")
        .withAttachmentResolver(reference -> "y")
        .withPageLinkResolver(pageNodeId -> "z");

    assertThat(options.withMediaResolver(null).mediaResolver()).isNull();
    assertThat(options.withAttachmentResolver(null).attachmentResolver()).isNull();
    assertThat(options.withPageLinkResolver(null).pageLinkResolver()).isNull();
  }

  @Test
  void builder_with_no_setters_equals_defaults() {
    assertThat(MarkdownOptions.builder().build()).isEqualTo(MarkdownOptions.defaults());
  }

  @Test
  void builder_sets_each_field() {
    MediaResolver media = attrs -> "m";
    AttachmentResolver attachment = reference -> "a";
    PageLinkResolver pageLink = pageNodeId -> "p";

    var options = MarkdownOptions.builder()
        .unknownNodePolicy(UnknownNodePolicy.SKIP)
        .imageSizeAttributes(true)
        .tableFallback(TableFallback.HTML)
        .mediaResolver(media)
        .htmlVisualMarks(true)
        .attachmentResolver(attachment)
        .pageLinkResolver(pageLink)
        .collapseHardBreaks(true)
        .documentTitle("My Page")
        .escapeParentheses(true)
        .build();

    assertThat(options.unknownNodePolicy()).isEqualTo(UnknownNodePolicy.SKIP);
    assertThat(options.imageSizeAttributes()).isTrue();
    assertThat(options.tableFallback()).isEqualTo(TableFallback.HTML);
    assertThat(options.mediaResolver()).isSameAs(media);
    assertThat(options.htmlVisualMarks()).isTrue();
    assertThat(options.attachmentResolver()).isSameAs(attachment);
    assertThat(options.pageLinkResolver()).isSameAs(pageLink);
    assertThat(options.collapseHardBreaks()).isTrue();
    assertThat(options.documentTitle()).isEqualTo("My Page");
    assertThat(options.escapeParentheses()).isTrue();
  }

  @Test
  void escape_parentheses_default_off_and_carry_through_other_withers() {
    var options = MarkdownOptions.defaults();
    assertThat(options.escapeParentheses()).isFalse();

    var enabled = options.withEscapeParentheses(true);
    assertThat(enabled.escapeParentheses()).isTrue();
    assertThat(enabled.withUnknownNodePolicy(UnknownNodePolicy.SKIP).escapeParentheses()).isTrue();
    assertThat(enabled.withDocumentTitle("My Page").escapeParentheses()).isTrue();
    assertThat(enabled.withEscapeParentheses(false).escapeParentheses()).isFalse();
  }

  @Test
  void html_visual_marks_default_off_and_carry_through_other_withers() {
    var options = MarkdownOptions.defaults();
    assertThat(options.htmlVisualMarks()).isFalse();

    var enabled = options.withHtmlVisualMarks(true);
    assertThat(enabled.htmlVisualMarks()).isTrue();
    assertThat(enabled.withUnknownNodePolicy(UnknownNodePolicy.SKIP).htmlVisualMarks()).isTrue();
    assertThat(enabled.withTableFallback(TableFallback.HTML).htmlVisualMarks()).isTrue();
  }

  @Test
  void collapse_hard_breaks_default_off_and_carry_through_other_withers() {
    var options = MarkdownOptions.defaults();
    assertThat(options.collapseHardBreaks()).isFalse();

    var enabled = options.withCollapseHardBreaks(true);
    assertThat(enabled.collapseHardBreaks()).isTrue();
    assertThat(enabled.withUnknownNodePolicy(UnknownNodePolicy.SKIP).collapseHardBreaks()).isTrue();
    assertThat(enabled.withTableFallback(TableFallback.HTML).collapseHardBreaks()).isTrue();
  }

  @Test
  void document_title_default_null_and_carry_through_other_withers() {
    var options = MarkdownOptions.defaults();
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

    var withSuffix = MarkdownOptions.defaults().withImageSizeAttributes(true);
    assertThat(AdfToMarkdown.with(withSuffix).toMarkdown(json).stripTrailing())
        .isEqualTo("![Sized](https://example.com/diagram.png){width=800 height=400}");
  }
}
