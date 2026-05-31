package dev.nthings.adf4j.renderer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.nthings.adf4j.AttachmentReference;
import dev.nthings.adf4j.HeadingReference;
import dev.nthings.adf4j.PageTitleResolver;
import dev.nthings.adf4j.RenderOptions;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.model.ExcerptKey;
import dev.nthings.adf4j.model.MacroContext;
import dev.nthings.adf4j.model.PageLinkResolver;
import dev.nthings.adf4j.model.UnknownNodePolicy;

record RendererState(
    int listDepth,
    boolean inTable,
    String pageTitle,
    String currentPageId,
    MacroContext macroContext,
    HeadingOutline headingOutline,
    List<ConfluenceRenderContext.ChildPage> childPages,
    Map<String, AttachmentReference> attachmentReferencesByTitle,
    PageLinkResolver linkResolver,
    PageTitleResolver pageTitleResolver,
    UnknownNodePolicy unknownNodePolicy,
    Set<ExcerptKey> activeExcerpts) {

  static RendererState root(RenderOptions options, HeadingOutline headingOutline) {
    var requiredOptions = Objects.requireNonNull(options, "options");
    var safeOutline = Objects.requireNonNullElseGet(headingOutline, HeadingOutline::empty);
    var confluenceContext = ConfluenceRenderContext.from(requiredOptions.context());
    return new RendererState(
        0,
        false,
        confluenceContext.pageTitle(),
        confluenceContext.currentPageId(),
        MacroContext.from(confluenceContext.excerpts(), safeOutline.headings()),
        safeOutline,
        confluenceContext.childPages(),
        confluenceContext.attachmentReferencesByTitle(),
        confluenceContext.pageLinkResolver(),
        confluenceContext.pageTitleResolver(),
        requiredOptions.unknownNodePolicy(),
        Set.of());
  }

  HeadingReference headingInfo(Heading heading) {
    return headingOutline.infoFor(heading);
  }

  RendererState withListDepth(int depth) {
    return copy(depth, inTable, activeExcerpts);
  }

  RendererState withTable(boolean table) {
    return copy(listDepth, table, activeExcerpts);
  }

  boolean isExcerptActive(ExcerptKey key) {
    return activeExcerpts.contains(key);
  }

  RendererState withExcerpt(ExcerptKey key) {
    var nextActiveExcerpts = new LinkedHashSet<>(activeExcerpts);
    nextActiveExcerpts.add(key);
    return copy(listDepth, inTable, Set.copyOf(nextActiveExcerpts));
  }

  private RendererState copy(
      int listDepth, boolean inTable, Set<ExcerptKey> activeExcerpts) {
    return new RendererState(
        listDepth,
        inTable,
        pageTitle,
        currentPageId,
        macroContext,
        headingOutline,
        childPages,
        attachmentReferencesByTitle,
        linkResolver,
        pageTitleResolver,
        unknownNodePolicy,
        activeExcerpts);
  }

}
