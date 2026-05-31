package dev.nthings.adf4j.confluence;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.nthings.adf4j.AttachmentReference;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.internal.AttachmentReferences;

public record ConfluenceRenderContext(
    String pageTitle,
    String currentPageId,
    Map<ExcerptKey, List<AdfBlock>> excerpts,
    PageLinkResolver pageLinkResolver,
    PageTitleResolver pageTitleResolver,
    Map<String, AttachmentReference> attachmentReferencesByTitle) {

  public ConfluenceRenderContext {
    excerpts = immutableExcerpts(excerpts);
    attachmentReferencesByTitle = immutableAttachmentReferencesByTitle(attachmentReferencesByTitle);
  }

  public static ConfluenceRenderContext empty() {
    return forPage("");
  }

  public static ConfluenceRenderContext forPage(String pageTitle) {
    return new ConfluenceRenderContext(pageTitle, null, Map.of(), null, null, Map.of());
  }

  public ConfluenceRenderContext withPageId(String pageId) {
    return copy(pageId, excerpts, pageLinkResolver, pageTitleResolver, attachmentReferencesByTitle);
  }

  public ConfluenceRenderContext withExcerpts(Map<ExcerptKey, List<AdfBlock>> excerptByKey) {
    return copy(
        currentPageId, excerptByKey, pageLinkResolver, pageTitleResolver, attachmentReferencesByTitle);
  }

  public ConfluenceRenderContext withPageLinkResolver(PageLinkResolver resolver) {
    return copy(currentPageId, excerpts, resolver, pageTitleResolver, attachmentReferencesByTitle);
  }

  public ConfluenceRenderContext withPageTitleResolver(PageTitleResolver resolver) {
    return copy(currentPageId, excerpts, pageLinkResolver, resolver, attachmentReferencesByTitle);
  }

  public ConfluenceRenderContext withAttachmentReferences(
      Iterable<AttachmentReference> attachmentReferences) {
    var safe = new LinkedHashMap<String, AttachmentReference>();
    if (attachmentReferences != null) {
      for (var attachmentReference : attachmentReferences) {
        if (attachmentReference == null) {
          continue;
        }

        var normalizedTitle = AttachmentReferences.normalizeTitle(attachmentReference.title());
        if (normalizedTitle == null
            || attachmentReference.fileId() == null
            || attachmentReference.fileId().isBlank()) {
          continue;
        }

        safe.putIfAbsent(normalizedTitle, attachmentReference);
      }
    }

    return copy(currentPageId, excerpts, pageLinkResolver, pageTitleResolver, safe);
  }

  public AttachmentReference attachmentReference(String title) {
    var normalizedTitle = AttachmentReferences.normalizeTitle(title);
    if (normalizedTitle == null) {
      return null;
    }

    return attachmentReferencesByTitle.get(normalizedTitle);
  }

  private static Map<ExcerptKey, List<AdfBlock>> immutableExcerpts(
      Map<ExcerptKey, List<AdfBlock>> excerpts) {
    if (excerpts == null || excerpts.isEmpty()) {
      return Map.of();
    }

    var converted = new HashMap<ExcerptKey, List<AdfBlock>>();
    excerpts.forEach(
        (key, blocks) -> {
          if (key == null) {
            return;
          }
          converted.put(key, blocks == null ? List.of() : List.copyOf(blocks));
        });
    return Map.copyOf(converted);
  }

  private static Map<String, AttachmentReference> immutableAttachmentReferencesByTitle(
      Map<String, AttachmentReference> attachmentReferencesByTitle) {
    if (attachmentReferencesByTitle == null || attachmentReferencesByTitle.isEmpty()) {
      return Map.of();
    }

    return Collections.unmodifiableMap(new LinkedHashMap<>(attachmentReferencesByTitle));
  }

  private ConfluenceRenderContext copy(
      String currentPageId,
      Map<ExcerptKey, List<AdfBlock>> excerpts,
      PageLinkResolver pageLinkResolver,
      PageTitleResolver pageTitleResolver,
      Map<String, AttachmentReference> attachmentReferencesByTitle) {
    return new ConfluenceRenderContext(
        pageTitle,
        currentPageId,
        excerpts,
        pageLinkResolver,
        pageTitleResolver,
        attachmentReferencesByTitle);
  }
}
