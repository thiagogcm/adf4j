package dev.nthings.adf4j.options;

import java.util.List;

import dev.nthings.adf4j.AdfToMarkdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageTreeResolverOptionsTests {

  // A block-level pagetree macro with no parameters.
  private static final String PAGETREE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "pagetree"
            }
          }
        ]
      }
      """;

  // The real-world structure Confluence emits: an inlineExtension nested in a paragraph. This is the
  // case that shipped as a literal {{pagetree}}; expansion must still produce a block list.
  private static final String INLINE_PAGETREE =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              {
                "type": "inlineExtension",
                "attrs": {
                  "extensionType": "com.atlassian.confluence.macro.core",
                  "extensionKey": "pagetree",
                  "parameters": { "macroParams": {} }
                }
              }
            ]
          }
        ]
      }
      """;

  // A pagetree rooted at the named page "Docs Home" with a startDepth parameter.
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
              "parameters": {
                "macroParams": { "root": { "value": "Docs Home" }, "startDepth": { "value": "2" } }
              }
            }
          }
        ]
      }
      """;

  // A pagetree whose root is the @self keyword.
  private static final String SELF_ROOTED_PAGETREE =
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
              "parameters": { "macroParams": { "root": { "value": "@self" } } }
            }
          }
        ]
      }
      """;

  // A children macro with no parameters (immediate children of the current page).
  private static final String CHILDREN =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "children"
            }
          }
        ]
      }
      """;

  // A children macro rooted at the named page "Guides" with a depth of 2.
  private static final String ROOTED_CHILDREN =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "children",
              "parameters": {
                "macroParams": { "page": { "value": "Guides" }, "depth": { "value": "2" } }
              }
            }
          }
        ]
      }
      """;

  @Test
  void default_leaves_the_pagetree_token() {
    assertThat(AdfToMarkdown.create().toMarkdown(PAGETREE)).isEqualTo("{{pagetree}}");
  }

  @Test
  void resolver_expands_the_tree_resolving_each_page_node_id_via_the_page_link_resolver() {
    var options = MarkdownOptions.defaults()
        .withPageTreeResolver(request -> List.of(
            new PageTreeEntry(0, "Getting Started", "111"),
            new PageTreeEntry(1, "Install", "222"),
            new PageTreeEntry(0, "API Reference", "333")))
        .withPageLinkResolver(pageNodeId -> "pages/" + pageNodeId + ".md");

    assertThat(AdfToMarkdown.with(options).toMarkdown(PAGETREE))
        .isEqualTo(
            """
            - [Getting Started](pages/111.md)
              - [Install](pages/222.md)
            - [API Reference](pages/333.md)""");
  }

  @Test
  void resolver_expands_an_inline_pagetree_into_a_block_list() {
    var options = MarkdownOptions.defaults()
        .withPageTreeResolver(request -> List.of(new PageTreeEntry(0, "Home", "home")))
        .withPageLinkResolver(pageNodeId -> "h.md");

    assertThat(AdfToMarkdown.with(options).toMarkdown(INLINE_PAGETREE)).isEqualTo("- [Home](h.md)");
  }

  @Test
  void resolver_escapes_entry_labels() {
    var options = MarkdownOptions.defaults()
        .withPageTreeResolver(request -> List.of(new PageTreeEntry(0, "A & B", "111")))
        .withPageLinkResolver(pageNodeId -> "https://x/a");

    assertThat(AdfToMarkdown.with(options).toMarkdown(PAGETREE)).isEqualTo("- [A \\& B](https://x/a)");
  }

  @Test
  void resolver_destinations_are_scheme_sanitized() {
    // A resolved page destination is untrusted input — a dangerous scheme is neutralized.
    var options = MarkdownOptions.defaults()
        .withPageTreeResolver(request -> List.of(new PageTreeEntry(0, "x", "111")))
        .withPageLinkResolver(pageNodeId -> "javascript:alert(1)");

    assertThat(AdfToMarkdown.with(options).toMarkdown(PAGETREE))
        .isEqualTo("- [x](javascript%3Aalert(1))");
  }

  @Test
  void an_entry_without_a_resolvable_destination_renders_as_plain_text() {
    // A page node id with no PageLinkResolver to resolve it leaves the (escaped) title, not a link.
    var options = MarkdownOptions.defaults()
        .withPageTreeResolver(request -> List.of(new PageTreeEntry(0, "Orphan", "999")));

    assertThat(AdfToMarkdown.with(options).toMarkdown(PAGETREE)).isEqualTo("- Orphan");
  }

  @Test
  void entry_depth_is_normalized_so_the_shallowest_entry_sits_at_column_zero() {
    var options = MarkdownOptions.defaults()
        .withPageTreeResolver(request -> List.of(
            new PageTreeEntry(2, "Top", "top"),
            new PageTreeEntry(3, "Child", "child")))
        .withPageLinkResolver(pageNodeId -> pageNodeId.equals("top") ? "t" : "c");

    assertThat(AdfToMarkdown.with(options).toMarkdown(PAGETREE))
        .isEqualTo(
            """
            - [Top](t)
              - [Child](c)""");
  }

  @Test
  void the_request_carries_the_named_root_and_the_full_parameter_map() {
    var captured = new PageTreeRequest[1];
    var options = MarkdownOptions.defaults().withPageTreeResolver(request -> {
      captured[0] = request;
      return List.of(new PageTreeEntry(0, "x", "x"));
    });

    AdfToMarkdown.with(options).toMarkdown(ROOTED_PAGETREE);

    assertThat(captured[0].macro()).isEqualTo(PageTreeMacro.PAGETREE);
    assertThat(captured[0].root()).isEqualTo("Docs Home");
    assertThat(captured[0].parameters())
        .containsEntry("root", "Docs Home")
        .containsEntry("startDepth", "2");
  }

  @Test
  void the_request_root_is_null_for_a_keyword_root_but_the_keyword_stays_in_the_parameters() {
    var captured = new PageTreeRequest[1];
    var options = MarkdownOptions.defaults().withPageTreeResolver(request -> {
      captured[0] = request;
      return List.of();
    });

    AdfToMarkdown.with(options).toMarkdown(SELF_ROOTED_PAGETREE);

    assertThat(captured[0].root()).isNull();
    assertThat(captured[0].parameters()).containsEntry("root", "@self");
  }

  @Test
  void resolver_that_declines_falls_back_to_the_token() {
    var declines = MarkdownOptions.defaults().withPageTreeResolver(request -> List.of());
    assertThat(AdfToMarkdown.with(declines).toMarkdown(PAGETREE)).isEqualTo("{{pagetree}}");

    var nulls = MarkdownOptions.defaults().withPageTreeResolver(request -> null);
    assertThat(AdfToMarkdown.with(nulls).toMarkdown(PAGETREE)).isEqualTo("{{pagetree}}");
  }

  @Test
  void resolver_that_throws_is_contained_and_falls_back_to_the_token() {
    // A failing lookup must not abort the conversion; it degrades to the token (named root kept).
    var options = MarkdownOptions.defaults().withPageTreeResolver(request -> {
      throw new RuntimeException("lookup failed");
    });

    assertThat(AdfToMarkdown.with(options).toMarkdown(ROOTED_PAGETREE))
        .isEqualTo("{{pagetree:Docs Home}}");
  }

  // --- children macro ---------------------------------------------------------------------------

  @Test
  void default_leaves_the_children_token() {
    assertThat(AdfToMarkdown.create().toMarkdown(CHILDREN)).isEqualTo("{{children}}");
  }

  @Test
  void the_same_resolver_expands_the_children_macro() {
    var options = MarkdownOptions.defaults()
        .withPageTreeResolver(request -> List.of(
            new PageTreeEntry(0, "First", "1"),
            new PageTreeEntry(0, "Second", "2")))
        .withPageLinkResolver(pageNodeId -> pageNodeId + ".md");

    assertThat(AdfToMarkdown.with(options).toMarkdown(CHILDREN))
        .isEqualTo(
            """
            - [First](1.md)
            - [Second](2.md)""");
  }

  @Test
  void the_children_request_reports_the_macro_its_page_root_and_params() {
    var captured = new PageTreeRequest[1];
    var options = MarkdownOptions.defaults().withPageTreeResolver(request -> {
      captured[0] = request;
      return List.of(new PageTreeEntry(0, "x", "x"));
    });

    AdfToMarkdown.with(options).toMarkdown(ROOTED_CHILDREN);

    assertThat(captured[0].macro()).isEqualTo(PageTreeMacro.CHILDREN);
    assertThat(captured[0].root()).isEqualTo("Guides");
    assertThat(captured[0].parameters())
        .containsEntry("page", "Guides")
        .containsEntry("depth", "2");
  }

  @Test
  void a_declining_resolver_keeps_the_children_depth_token() {
    // Falling back preserves the existing {{children:<depth>}} token for a bounded depth.
    var options = MarkdownOptions.defaults().withPageTreeResolver(request -> List.of());

    assertThat(AdfToMarkdown.with(options).toMarkdown(ROOTED_CHILDREN)).isEqualTo("{{children:2}}");
  }
}
