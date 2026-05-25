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
import dev.nthings.adf4j.model.ExcerptKey;
import dev.nthings.adf4j.model.MacroContext;
import dev.nthings.adf4j.model.PageLinkResolver;
import dev.nthings.adf4j.model.UnknownNodePolicy;

record RenderContext(
    int listDepth,
    boolean inTable,
    RenderingStrategy strategy,
    String pageTitle,
    String currentPageId,
    MacroContext macroContext,
    HeadingOutline headingOutline,
    List<RenderOptions.ChildPage> childPages,
    Map<String, AttachmentReference> attachmentReferencesByTitle,
    PageLinkResolver linkResolver,
    PageTitleResolver pageTitleResolver,
    UnknownNodePolicy unknownNodePolicy,
    Set<ExcerptKey> activeExcerpts) {

  static RenderContext root(
      RenderOptions options, HeadingOutline headingOutline, RenderingStrategy strategy) {
    var requiredOptions = Objects.requireNonNull(options, "options");
    var safeOutline = Objects.requireNonNullElseGet(headingOutline, HeadingOutline::empty);
    var renderingStrategy = Objects.requireNonNullElseGet(strategy, RenderingStrategies::storage);
    return new RenderContext(
        0,
        false,
        renderingStrategy,
        requiredOptions.pageTitle(),
        requiredOptions.currentPageId(),
        MacroContext.from(requiredOptions.excerpts(), safeOutline.headings()),
        safeOutline,
        requiredOptions.childPages(),
        requiredOptions.attachmentReferencesByTitle(),
        requiredOptions.pageLinkResolver(),
        requiredOptions.pageTitleResolver(),
        requiredOptions.unknownNodePolicy(),
        Set.of());
  }

  HeadingReference headingInfo(Heading heading) {
    return headingOutline.infoFor(heading);
  }

  RenderContext withListDepth(int depth) {
    return copy(depth, inTable, activeExcerpts);
  }

  RenderContext withTable(boolean table) {
    return copy(listDepth, table, activeExcerpts);
  }

  boolean isExcerptActive(ExcerptKey key) {
    return activeExcerpts.contains(key);
  }

  RenderContext withExcerpt(ExcerptKey key) {
    var nextActiveExcerpts = new LinkedHashSet<>(activeExcerpts);
    nextActiveExcerpts.add(key);
    return copy(listDepth, inTable, Set.copyOf(nextActiveExcerpts));
  }

  private RenderContext copy(
      int listDepth, boolean inTable, Set<ExcerptKey> activeExcerpts) {
    return new RenderContext(
        listDepth,
        inTable,
        strategy,
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
