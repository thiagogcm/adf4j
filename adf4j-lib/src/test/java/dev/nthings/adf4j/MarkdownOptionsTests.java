package dev.nthings.adf4j;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.options.MarkdownOptions;
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
    var options = new MarkdownOptions(null, null, false, null, null);

    assertThat(options.unknownNodePolicy()).isEqualTo(UnknownNodePolicy.PLACEHOLDER);
    assertThat(options.context()).isEqualTo(ConfluenceRenderContext.empty());
    assertThat(options.tableFallback()).isEqualTo(TableFallback.HTML);
    assertThat(options.mediaResolver()).isNull();
    assertThat(options.withUnknownNodePolicy(UnknownNodePolicy.SKIP).unknownNodePolicy())
        .isEqualTo(UnknownNodePolicy.SKIP);
    assertThat(options.withContext(null).context()).isEqualTo(ConfluenceRenderContext.empty());
  }

  @Test
  void table_fallback_and_media_resolver_default_and_carry_through_other_withers() {
    var options = MarkdownOptions.defaults();
    assertThat(options.tableFallback()).isEqualTo(TableFallback.HTML);
    assertThat(options.mediaResolver()).isNull();

    var fallback = options.withTableFallback(TableFallback.GFM_PROMOTE_FIRST_ROW);
    assertThat(fallback.tableFallback()).isEqualTo(TableFallback.GFM_PROMOTE_FIRST_ROW);
    assertThat(fallback.withUnknownNodePolicy(UnknownNodePolicy.SKIP).tableFallback())
        .isEqualTo(TableFallback.GFM_PROMOTE_FIRST_ROW);
    assertThat(fallback.withImageSizeAttributes(true).tableFallback())
        .isEqualTo(TableFallback.GFM_PROMOTE_FIRST_ROW);

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
