package dev.nthings.adf4j.options;

import dev.nthings.adf4j.AdfToMarkdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageLinkResolverOptionsTests {

  // A text link whose href is a Confluence page URL (page node id 12345).
  private static final String PAGE_TEXT_LINK =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              {
                "type": "text",
                "text": "See the spec",
                "marks": [
                  { "type": "link", "attrs": { "href": "https://example.atlassian.net/wiki/spaces/OF/pages/12345" } }
                ]
              }
            ]
          }
        ]
      }
      """;

  // A url-only block smart-card pointing at a Confluence page (page node id 54321).
  private static final String PAGE_BLOCK_CARD =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "blockCard",
            "attrs": { "url": "https://example.atlassian.net/wiki/spaces/OF/pages/54321" }
          }
        ]
      }
      """;

  // An external (non-page) link the resolver must not touch.
  private static final String EXTERNAL_LINK =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              {
                "type": "text",
                "text": "docs",
                "marks": [{ "type": "link", "attrs": { "href": "https://example.com/docs" } }]
              }
            ]
          }
        ]
      }
      """;

  private static MarkdownOptions resolving(PageLinkResolver resolver) {
    return MarkdownOptions.defaults().withPageLinkResolver(resolver);
  }

  @Test
  void default_leaves_a_page_text_link_pointing_at_the_live_site() {
    var markdown = AdfToMarkdown.create().toMarkdown(PAGE_TEXT_LINK).strip();

    assertThat(markdown)
        .isEqualTo("[See the spec](https://example.atlassian.net/wiki/spaces/OF/pages/12345)");
  }

  @Test
  void resolver_rewrites_a_page_text_link_destination_keeping_the_label() {
    var options = resolving(pageNodeId -> "pages/" + pageNodeId + ".md");

    var markdown = AdfToMarkdown.with(options).toMarkdown(PAGE_TEXT_LINK).strip();

    assertThat(markdown).isEqualTo("[See the spec](pages/12345.md)");
  }

  @Test
  void resolver_rewrites_a_page_card_destination_keeping_the_url_as_label() {
    var options = resolving(pageNodeId -> "pages/" + pageNodeId + ".md");

    var markdown = AdfToMarkdown.with(options).toMarkdown(PAGE_BLOCK_CARD).strip();

    assertThat(markdown)
        .isEqualTo(
            "[https://example.atlassian.net/wiki/spaces/OF/pages/54321](pages/54321.md)");
  }

  @Test
  void default_renders_a_page_card_as_an_autolink() {
    var markdown = AdfToMarkdown.create().toMarkdown(PAGE_BLOCK_CARD).strip();

    assertThat(markdown)
        .isEqualTo("<https://example.atlassian.net/wiki/spaces/OF/pages/54321>");
  }

  @Test
  void resolver_is_not_consulted_for_external_links() {
    var options = resolving(pageNodeId -> "SHOULD_NOT_APPEAR");

    var markdown = AdfToMarkdown.with(options).toMarkdown(EXTERNAL_LINK).strip();

    assertThat(markdown).isEqualTo("[docs](https://example.com/docs)");
  }

  @Test
  void resolver_returning_null_leaves_the_original_href() {
    var options = resolving(pageNodeId -> null);

    var markdown = AdfToMarkdown.with(options).toMarkdown(PAGE_TEXT_LINK).strip();

    assertThat(markdown)
        .isEqualTo("[See the spec](https://example.atlassian.net/wiki/spaces/OF/pages/12345)");
  }
}
