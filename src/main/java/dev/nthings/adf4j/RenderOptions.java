package dev.nthings.adf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.model.ExcerptKey;
import dev.nthings.adf4j.model.PageLinkResolver;
import dev.nthings.adf4j.model.UnknownNodePolicy;

public record RenderOptions(
    String pageTitle,
    String currentPageId,
    Map<ExcerptKey, List<AdfBlock>> excerpts,
    PageLinkResolver pageLinkResolver,
    PageTitleResolver pageTitleResolver,
    UnknownNodePolicy unknownNodePolicy,
    Map<String, AttachmentReference> attachmentReferencesByTitle,
    List<ChildPage> childPages) {

  public RenderOptions {
    excerpts = excerpts == null ? Map.of() : Map.copyOf(excerpts);
    unknownNodePolicy = unknownNodePolicy == null ? UnknownNodePolicy.PLACEHOLDER : unknownNodePolicy;
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

  public static RenderOptions defaults(String pageTitle) {
    return new RenderOptions(
        pageTitle, null, Map.of(), null, null, UnknownNodePolicy.PLACEHOLDER, Map.of(), List.of());
  }

  public RenderOptions withPageId(String pageId) {
    return copy(
        pageId,
        excerpts,
        pageLinkResolver,
        pageTitleResolver,
        unknownNodePolicy,
        attachmentReferencesByTitle,
        childPages);
  }

  public RenderOptions withExcerpts(Map<ExcerptKey, List<AdfBlock>> excerptByKey) {
    return copy(
        currentPageId,
        excerptByKey,
        pageLinkResolver,
        pageTitleResolver,
        unknownNodePolicy,
        attachmentReferencesByTitle,
        childPages);
  }

  public RenderOptions withPageLinkResolver(PageLinkResolver resolver) {
    return copy(
        currentPageId,
        excerpts,
        resolver,
        pageTitleResolver,
        unknownNodePolicy,
        attachmentReferencesByTitle,
        childPages);
  }

  public RenderOptions withPageTitleResolver(PageTitleResolver resolver) {
    return copy(
        currentPageId,
        excerpts,
        pageLinkResolver,
        resolver,
        unknownNodePolicy,
        attachmentReferencesByTitle,
        childPages);
  }

  public RenderOptions withUnknownNodePolicy(UnknownNodePolicy policy) {
    var resolvedPolicy = policy == null ? UnknownNodePolicy.PLACEHOLDER : policy;
    return copy(
        currentPageId,
        excerpts,
        pageLinkResolver,
        pageTitleResolver,
        resolvedPolicy,
        attachmentReferencesByTitle,
        childPages);
  }

  public RenderOptions withAttachmentReferences(
      Iterable<AttachmentReference> attachmentReferences) {
    var safe = new LinkedHashMap<String, AttachmentReference>();
    if (attachmentReferences != null) {
      for (var attachmentReference : attachmentReferences) {
        if (attachmentReference == null) {
          continue;
        }

        var normalizedTitle = AttachmentReferences.normalizeTitle(attachmentReference.title());
        if (normalizedTitle == null || attachmentReference.fileId() == null || attachmentReference.fileId().isBlank()) {
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
        unknownNodePolicy,
        safe,
        childPages);
  }

  public RenderOptions withChildPages(Iterable<ChildPage> pages) {
    return copy(
        currentPageId,
        excerpts,
        pageLinkResolver,
        pageTitleResolver,
        unknownNodePolicy,
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

  private RenderOptions copy(
      String currentPageId,
      Map<ExcerptKey, List<AdfBlock>> excerpts,
      PageLinkResolver pageLinkResolver,
      PageTitleResolver pageTitleResolver,
      UnknownNodePolicy unknownNodePolicy,
      Map<String, AttachmentReference> attachmentReferencesByTitle,
      List<ChildPage> childPages) {
    return new RenderOptions(
        pageTitle,
        currentPageId,
        excerpts,
        pageLinkResolver,
        pageTitleResolver,
        unknownNodePolicy,
        attachmentReferencesByTitle,
        childPages);
  }
}
