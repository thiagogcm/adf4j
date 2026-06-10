package dev.nthings.adf4j;

import java.util.List;

import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.PageTreeEntry;
import dev.nthings.adf4j.options.PageTreeMacro;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers {@code MarkdownResult.unresolved()}: the lookups a render's resolvers declined. */
class UnresolvedReferencesTests {

  // A paragraph linking to Confluence page 12345 by URL.
  private static final String PAGE_LINK =
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
                "text": "spec",
                "marks": [{ "type": "link", "attrs": { "href": "https://x.example/wiki/spaces/S/pages/12345/Spec" } }]
              }
            ]
          }
        ]
      }
      """;

  // A pagetree macro rooted at "Docs Home".
  private static final String ROOTED_PAGETREE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "pagetree",
              "parameters": { "macroParams": { "root": { "value": "Docs Home" } } }
            }
          }
        ]
      }
      """;

  @Test
  void a_page_link_the_resolver_declines_is_reported_with_the_original_href_kept() {
    var options = MarkdownOptions.defaults().withPageLinkResolver(pageNodeId -> null);

    var result = AdfToMarkdown.with(options).convert(PAGE_LINK);

    assertThat(result.unresolved().pageIds()).containsExactly("12345");
    assertThat(result.body()).contains("(https://x.example/wiki/spaces/S/pages/12345/Spec)");
  }

  @Test
  void a_resolved_page_link_reports_nothing() {
    var options = MarkdownOptions.defaults().withPageLinkResolver(pageNodeId -> "pages/spec.md");

    var result = AdfToMarkdown.with(options).convert(PAGE_LINK);

    assertThat(result.unresolved().isEmpty()).isTrue();
  }

  @Test
  void without_a_page_link_resolver_no_lookup_happens_and_nothing_is_reported() {
    var result = AdfToMarkdown.create().convert(PAGE_LINK);

    assertThat(result.unresolved().isEmpty()).isTrue();
  }

  @Test
  void a_tree_macro_that_falls_back_to_the_placeholder_is_reported() {
    // No resolver at all: the {{pagetree:Docs Home}} token leaks, and the result says so.
    var result = AdfToMarkdown.create().convert(ROOTED_PAGETREE);

    assertThat(result.unresolved().pageTreeRefs())
        .containsExactly(new PageTreeReference(PageTreeMacro.PAGETREE, "Docs Home"));

    // A resolver that declines with null reports the same.
    var declined = AdfToMarkdown.with(
            MarkdownOptions.defaults().withPageTreeResolver(request -> null))
        .convert(ROOTED_PAGETREE);

    assertThat(declined.unresolved().pageTreeRefs())
        .containsExactly(new PageTreeReference(PageTreeMacro.PAGETREE, "Docs Home"));
  }

  @Test
  void a_tree_macro_resolved_to_an_empty_list_counts_as_resolved() {
    var options = MarkdownOptions.defaults().withPageTreeResolver(request -> List.of());

    var result = AdfToMarkdown.with(options).convert(ROOTED_PAGETREE);

    assertThat(result.unresolved().isEmpty()).isTrue();
    assertThat(result.body()).isEmpty();
  }

  @Test
  void a_throwing_tree_resolver_is_reported_like_a_decline() {
    var options = MarkdownOptions.defaults().withPageTreeResolver(request -> {
      throw new RuntimeException("lookup failed");
    });

    var result = AdfToMarkdown.with(options).convert(ROOTED_PAGETREE);

    assertThat(result.unresolved().pageTreeRefs())
        .containsExactly(new PageTreeReference(PageTreeMacro.PAGETREE, "Docs Home"));
  }

  @Test
  void declined_tree_entry_ids_are_reported_alongside_resolved_ones() {
    var options = MarkdownOptions.defaults()
        .withPageTreeResolver(request -> List.of(
            new PageTreeEntry(0, "Known", "111"),
            new PageTreeEntry(0, "Gone", "222")))
        .withPageLinkResolver(pageNodeId -> "111".equals(pageNodeId) ? "known.md" : null);

    var result = AdfToMarkdown.with(options).convert(ROOTED_PAGETREE);

    assertThat(result.unresolved().pageIds()).containsExactly("222");
    assertThat(result.unresolved().pageTreeRefs()).isEmpty();
  }
}
