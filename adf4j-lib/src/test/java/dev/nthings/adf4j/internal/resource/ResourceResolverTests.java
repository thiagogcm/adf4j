package dev.nthings.adf4j.internal.resource;

import static dev.nthings.adf4j.ast.Adf.externalMedia;
import static dev.nthings.adf4j.ast.Adf.media;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.PageTreeMacro;
import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.PageTreeEntry;
import dev.nthings.adf4j.options.ResolverHooks;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceResolverTests {

  private static final String PAGE_URL = "https://example.atlassian.net/wiki/spaces/OF/pages/12345";

  private final CallbackTracker callbacks = new CallbackTracker();

  private ResourceResolver resolver(ConversionOptions options, AttachmentReference... attachments) {
    return new ResourceResolver(
        options.resolvers(), AttachmentInventory.from(Arrays.asList(attachments)), callbacks);
  }

  @Test
  void a_media_node_url_wins_without_calling_any_hook() {
    var resolver =
        resolver(
            ConversionOptions.defaults()
                .withMediaResolver(
                    media -> {
                      throw new AssertionError("media hook must not run");
                    })
                .withFileResolver(
                    id -> {
                      throw new AssertionError("file hook must not run");
                    }));

    assertThat(resolver.mediaSource(externalMedia("https://cdn.example/a"), "/content/0"))
        .isEqualTo("https://cdn.example/a");
    assertThat(callbacks.failureCount()).isZero();
  }

  @Test
  void the_media_hook_wins_over_the_file_hook_and_the_inventory() {
    var reference = new AttachmentReference("abc", "diagram.png", "image/png", "inventory/abc.png");
    var resolver =
        resolver(
            ConversionOptions.defaults()
                .withMediaResolver(media -> "media/" + media.id())
                .withFileResolver(id -> "file/" + id),
            reference);

    assertThat(resolver.mediaSource(media("abc", "collection"), "/content/0"))
        .isEqualTo("media/abc");
    assertThat(callbacks.failureCount()).isZero();
  }

  @Test
  void a_blank_media_hook_falls_through_to_the_file_hook() {
    var resolver =
        resolver(
            ConversionOptions.defaults()
                .withMediaResolver(media -> "  ")
                .withFileResolver(id -> "file/" + id));

    assertThat(resolver.mediaSource(media("abc", "collection"), "/content/0"))
        .isEqualTo("file/abc");
    assertThat(callbacks.failureCount()).isZero();
  }

  @Test
  void a_declined_media_hook_falls_through_to_the_inventory_download_url() {
    var reference = new AttachmentReference("abc", "diagram.png", "image/png", "inventory/abc.png");
    var resolver =
        resolver(ConversionOptions.defaults().withMediaResolver(media -> null), reference);

    assertThat(resolver.mediaSource(media("abc", "collection"), "/content/0"))
        .isEqualTo("inventory/abc.png");
    assertThat(callbacks.failureCount()).isZero();
  }

  @Test
  void failing_media_and_file_hooks_keep_their_failures_when_the_inventory_rescues_the_url() {
    var reference = new AttachmentReference("abc", "diagram.png", "image/png", "inventory/abc.png");
    var resolver =
        resolver(
            ConversionOptions.defaults()
                .withMediaResolver(
                    media -> {
                      throw new IllegalStateException("media offline");
                    })
                .withFileResolver(
                    id -> {
                      throw new IllegalStateException("file offline");
                    }),
            reference);

    assertThat(resolver.mediaSource(media("abc", "collection"), "/content/0"))
        .isEqualTo("inventory/abc.png");
    assertThat(callbacks.failureCount()).isEqualTo(2);
    assertThat(callbacks.reasonSince(0)).isEqualTo(UnresolvedResource.Reason.CALLBACK_FAILED);
    assertThat(callbacks.diagnostics())
        .extracting(Diagnostic::message)
        .containsExactly(
            "MediaResolver failed: media offline", "FileResolver failed: file offline");
    assertThat(callbacks.diagnostics())
        .allSatisfy(
            diagnostic -> {
              assertThat(diagnostic.code()).isEqualTo(Diagnostic.Code.CALLBACK_FAILED);
              assertThat(diagnostic.paths()).containsExactly("/content/0");
            });
    assertThat(callbacks.diagnostics())
        .extracting(diagnostic -> diagnostic.details().get("callback"))
        .containsExactly("MediaResolver", "FileResolver");
  }

  @Test
  void media_without_any_source_is_null_and_has_no_answer() {
    var resolver = resolver(ConversionOptions.defaults());

    assertThat(resolver.mediaSource(media("abc", "collection"), "/content/0")).isNull();
    assertThat(callbacks.reasonSince(0)).isEqualTo(UnresolvedResource.Reason.NO_ANSWER);
  }

  @Test
  void unknown_and_empty_inventories_both_leave_media_without_a_download_url() {
    var unknown =
        new ResourceResolver(ResolverHooks.defaults(), AttachmentInventory.from(null), callbacks);
    var empty =
        new ResourceResolver(
            ResolverHooks.defaults(), AttachmentInventory.from(List.of()), callbacks);

    assertThat(unknown.mediaSource(media("abc", "collection"), "/content/0")).isNull();
    assertThat(empty.mediaSource(media("abc", "collection"), "/content/0")).isNull();
  }

  @Test
  void an_attachment_hook_wins_over_the_file_hook_and_the_download_url() {
    var reference =
        new AttachmentReference("file-1", "guide.pdf", "application/pdf", "inventory/guide.pdf");
    var resolver =
        resolver(
            ConversionOptions.defaults()
                .withAttachmentResolver(reference1 -> "attachment/" + reference1.fileId())
                .withFileResolver(id -> "file/" + id),
            reference);

    assertThat(resolver.attachmentSource(reference, "/content/0")).isEqualTo("attachment/file-1");
    assertThat(callbacks.failureCount()).isZero();
  }

  @Test
  void a_blank_attachment_hook_falls_through_to_the_file_hook() {
    var reference =
        new AttachmentReference("file-1", "guide.pdf", "application/pdf", "inventory/guide.pdf");
    var resolver =
        resolver(
            ConversionOptions.defaults()
                .withAttachmentResolver(reference1 -> " ")
                .withFileResolver(id -> "file/" + id),
            reference);

    assertThat(resolver.attachmentSource(reference, "/content/0")).isEqualTo("file/file-1");
  }

  @Test
  void a_declined_attachment_hook_falls_through_to_the_download_url() {
    var reference =
        new AttachmentReference("file-1", "guide.pdf", "application/pdf", "inventory/guide.pdf");
    var resolver =
        resolver(ConversionOptions.defaults().withAttachmentResolver(reference1 -> null));

    assertThat(resolver.attachmentSource(reference, "/content/0")).isEqualTo("inventory/guide.pdf");
  }

  @Test
  void failing_attachment_and_file_hooks_keep_their_failures_when_the_download_url_rescues_it() {
    var reference =
        new AttachmentReference("file-1", "guide.pdf", "application/pdf", "inventory/guide.pdf");
    var resolver =
        resolver(
            ConversionOptions.defaults()
                .withAttachmentResolver(
                    reference1 -> {
                      throw new IllegalStateException("attachment offline");
                    })
                .withFileResolver(
                    id -> {
                      throw new IllegalStateException("file offline");
                    }),
            reference);

    assertThat(resolver.attachmentSource(reference, "/content/0")).isEqualTo("inventory/guide.pdf");
    assertThat(callbacks.failureCount()).isEqualTo(2);
    assertThat(callbacks.reasonSince(0)).isEqualTo(UnresolvedResource.Reason.CALLBACK_FAILED);
    assertThat(callbacks.diagnostics())
        .extracting(Diagnostic::message)
        .containsExactly(
            "AttachmentResolver failed: attachment offline", "FileResolver failed: file offline");
  }

  @Test
  void an_attachment_without_any_source_is_null_and_has_no_answer() {
    var reference = new AttachmentReference("file-1", "guide.pdf", "application/pdf");
    var resolver = resolver(ConversionOptions.defaults());

    assertThat(resolver.attachmentSource(reference, "/content/0")).isNull();
    assertThat(callbacks.reasonSince(0)).isEqualTo(UnresolvedResource.Reason.NO_ANSWER);
  }

  @Test
  void a_blank_download_url_is_not_a_source() {
    var reference = new AttachmentReference("file-1", "guide.pdf", "application/pdf", "  ");
    var resolver = resolver(ConversionOptions.defaults());

    assertThat(resolver.attachmentSource(reference, "/content/0")).isNull();
  }

  @Test
  void page_href_rewrites_page_links_and_keeps_other_urls() {
    var resolver =
        resolver(
            ConversionOptions.defaults().withPageLinkResolver(pageNodeId -> "pages/" + pageNodeId));

    assertThat(resolver.linkedPageId(PAGE_URL, Attributes.empty())).isEqualTo("12345");
    assertThat(resolver.pageHref(PAGE_URL, Attributes.empty(), "/content/0"))
        .isEqualTo("pages/12345");
    assertThat(resolver.pageHref("https://example.com/docs", Attributes.empty(), "/content/0"))
        .isEqualTo("https://example.com/docs");
    assertThat(callbacks.failureCount()).isZero();
  }

  @Test
  void page_href_without_a_resolver_returns_the_original_and_calls_nothing() {
    var resolver = resolver(ConversionOptions.defaults());

    assertThat(resolver.linkedPageId(PAGE_URL, Attributes.empty())).isNull();
    assertThat(resolver.pageHref(PAGE_URL, Attributes.empty(), "/content/0")).isEqualTo(PAGE_URL);
    assertThat(callbacks.failureCount()).isZero();
  }

  @Test
  void a_blank_or_absent_page_id_does_not_call_the_page_hook() {
    var resolver =
        resolver(
            ConversionOptions.defaults()
                .withPageLinkResolver(
                    pageNodeId -> {
                      throw new AssertionError("page hook must not run");
                    }));

    assertThat(resolver.pageId(null, "/content/0")).isNull();
    assertThat(resolver.pageId("  ", "/content/0")).isNull();
    assertThat(resolver.pageId("", "/content/0")).isNull();
    assertThat(callbacks.failureCount()).isZero();
  }

  @Test
  void a_failing_page_hook_keeps_the_original_href_and_records_the_failure() {
    var resolver =
        resolver(
            ConversionOptions.defaults()
                .withPageLinkResolver(
                    pageNodeId -> {
                      throw new IllegalStateException("page offline");
                    }));

    assertThat(resolver.pageHref(PAGE_URL, Attributes.empty(), "/content/0")).isEqualTo(PAGE_URL);
    assertThat(callbacks.failureCount()).isOne();
    assertThat(callbacks.reasonSince(0)).isEqualTo(UnresolvedResource.Reason.CALLBACK_FAILED);
    assertThat(callbacks.diagnostics())
        .singleElement()
        .satisfies(
            diagnostic -> {
              assertThat(diagnostic.code()).isEqualTo(Diagnostic.Code.CALLBACK_FAILED);
              assertThat(diagnostic.message()).isEqualTo("PageLinkResolver failed: page offline");
              assertThat(diagnostic.paths()).containsExactly("/content/0");
              assertThat(diagnostic.details()).containsEntry("callback", "PageLinkResolver");
            });
  }

  @Test
  void a_declined_page_hook_has_no_answer() {
    var resolver = resolver(ConversionOptions.defaults().withPageLinkResolver(pageNodeId -> null));

    assertThat(resolver.pageId("12345", "/content/0")).isNull();
    assertThat(callbacks.reasonSince(0)).isEqualTo(UnresolvedResource.Reason.NO_ANSWER);
  }

  @Test
  void a_page_tree_hook_returns_structured_entries_without_formatting() {
    var reference = new PageTreeReference(PageTreeMacro.PAGETREE, "12345", Map.of());
    var entries = List.of(new PageTreeEntry(0, "A *literal* title", "456"));
    var resolver =
        resolver(
            ConversionOptions.defaults()
                .withPageTreeResolver(
                    request -> {
                      assertThat(request).isSameAs(reference);
                      return entries;
                    }));

    assertThat(resolver.pageTree(reference, "/content/0")).containsExactlyElementsOf(entries);
    assertThat(callbacks.diagnostics()).isEmpty();
  }

  @Test
  void an_empty_page_tree_is_distinct_from_an_absent_or_declined_answer() {
    var reference = new PageTreeReference(PageTreeMacro.PAGETREE, null, Map.of());

    assertThat(resolver(ConversionOptions.defaults()).pageTree(reference, "/content/0")).isNull();
    assertThat(
            resolver(ConversionOptions.defaults().withPageTreeResolver(request -> null))
                .pageTree(reference, "/content/0"))
        .isNull();
    assertThat(
            resolver(ConversionOptions.defaults().withPageTreeResolver(request -> List.of()))
                .pageTree(reference, "/content/0"))
        .isEmpty();
    assertThat(callbacks.diagnostics()).isEmpty();
  }

  @Test
  void a_page_tree_failure_keeps_its_callback_name_and_source_path() {
    var reference = new PageTreeReference(PageTreeMacro.PAGETREE, null, Map.of());
    var resolver =
        resolver(
            ConversionOptions.defaults()
                .withPageTreeResolver(
                    request -> {
                      throw new IllegalStateException("hierarchy offline");
                    }));

    assertThat(resolver.pageTree(reference, "/content/3")).isNull();
    assertThat(callbacks.reasonSince(0)).isEqualTo(UnresolvedResource.Reason.CALLBACK_FAILED);
    assertThat(callbacks.diagnostics())
        .singleElement()
        .satisfies(
            diagnostic -> {
              assertThat(diagnostic.message())
                  .isEqualTo("PageTreeResolver failed: hierarchy offline");
              assertThat(diagnostic.paths()).containsExactly("/content/3");
              assertThat(diagnostic.details()).containsEntry("callback", "PageTreeResolver");
            });
  }
}
