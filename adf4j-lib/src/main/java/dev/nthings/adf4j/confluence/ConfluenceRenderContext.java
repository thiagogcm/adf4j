package dev.nthings.adf4j.confluence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.nthings.adf4j.AdfRenderContext;
import dev.nthings.adf4j.AttachmentReference;
import dev.nthings.adf4j.PageTitleResolver;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.model.ExcerptKey;
import dev.nthings.adf4j.model.PageLinkResolver;

public record ConfluenceRenderContext(
    String pageTitle,
    String currentPageId,
    Map<ExcerptKey, List<AdfBlock>> excerpts,
    PageLinkResolver pageLinkResolver,
    PageTitleResolver pageTitleResolver,
    Map<String, AttachmentReference> attachmentReferencesByTitle,
    List<ChildPage> childPages)
    implements AdfRenderContext {

  public ConfluenceRenderContext {
    excerpts = immutableExcerpts(excerpts);
    attachmentReferencesByTitle = immutableAttachmentReferencesByTitle(attachmentReferencesByTitle);
    childPages = childPages == null ? List.of() : List.copyOf(childPages);
  }

  public record ChildPage(String nodeId, String title, List<ChildPage> children) {

    public ChildPage(String nodeId, String title) {
      this(nodeId, title, List.of());
    }

    public ChildPage {
      children = children == null ? List.of() : List.copyOf(children);
    }
  }

  public static ConfluenceRenderContext empty() {
    return forPage("");
  }

  public static ConfluenceRenderContext forPage(String pageTitle) {
    return new ConfluenceRenderContext(
        pageTitle, null, Map.of(), null, null, Map.of(), List.of());
  }

  public static ConfluenceRenderContext from(AdfRenderContext context) {
    return context instanceof ConfluenceRenderContext confluenceContext
        ? confluenceContext
        : empty();
  }

  public ConfluenceRenderContext withPageId(String pageId) {
    return copy(
        pageId,
        excerpts,
        pageLinkResolver,
        pageTitleResolver,
        attachmentReferencesByTitle,
        childPages);
  }

  public ConfluenceRenderContext withExcerpts(Map<ExcerptKey, List<AdfBlock>> excerptByKey) {
    return copy(
        currentPageId,
        excerptByKey,
        pageLinkResolver,
        pageTitleResolver,
        attachmentReferencesByTitle,
        childPages);
  }

  public ConfluenceRenderContext withPageLinkResolver(PageLinkResolver resolver) {
    return copy(
        currentPageId,
        excerpts,
        resolver,
        pageTitleResolver,
        attachmentReferencesByTitle,
        childPages);
  }

  public ConfluenceRenderContext withPageTitleResolver(PageTitleResolver resolver) {
    return copy(
        currentPageId,
        excerpts,
        pageLinkResolver,
        resolver,
        attachmentReferencesByTitle,
        childPages);
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

    return copy(
        currentPageId,
        excerpts,
        pageLinkResolver,
        pageTitleResolver,
        safe,
        childPages);
  }

  public ConfluenceRenderContext withChildPages(Iterable<ChildPage> pages) {
    return copy(
        currentPageId,
        excerpts,
        pageLinkResolver,
        pageTitleResolver,
        attachmentReferencesByTitle,
        sanitizeChildPages(pages));
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

  private static List<ChildPage> sanitizeChildPages(Iterable<ChildPage> pages) {
    var safe = new ArrayList<ChildPage>();
    if (pages == null) {
      return safe;
    }

    for (var page : pages) {
      var sanitized = sanitizeChildPage(page);
      if (sanitized != null) {
        safe.add(sanitized);
      }
    }

    return safe;
  }

  private static ChildPage sanitizeChildPage(ChildPage page) {
    if (page == null || page.nodeId() == null || page.nodeId().isBlank() || page.title() == null
        || page.title().isBlank()) {
      return null;
    }

    return new ChildPage(page.nodeId(), page.title(), sanitizeChildPages(page.children()));
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
      Map<String, AttachmentReference> attachmentReferencesByTitle,
      List<ChildPage> childPages) {
    return new ConfluenceRenderContext(
        pageTitle,
        currentPageId,
        excerpts,
        pageLinkResolver,
        pageTitleResolver,
        attachmentReferencesByTitle,
        childPages);
  }
}
