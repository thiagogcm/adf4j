package dev.nthings.adf4j.renderer;

import java.util.Objects;
import java.util.stream.Stream;

import dev.nthings.adf4j.ast.CardAttrs;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.internal.MarkdownText;

/**
 * Renders smart-link nodes (block/inline/embed cards) and resolves link targets: rewriting internal
 * Confluence page links and substituting resolved page titles for bare URLs. This is the single
 * place that understands how a Confluence href maps to a Markdown link.
 */
final class CardRenderer {

  String renderBlockCard(CardAttrs attrs, RendererState context) {
    var renderedUrl = renderCardUrl(attrs, context);
    if (renderedUrl != null) {
      return renderedUrl;
    }

    var identifier = Stream.of(attrs.datasourceId(), attrs.localId())
        .filter(s -> s != null && !s.isBlank())
        .findFirst()
        .orElse(null);
    if (identifier == null || identifier.isBlank()) {
      return "[Card]";
    }
    return "[Card: %s]".formatted(identifier);
  }

  String renderInlineCard(CardAttrs attrs, RendererState context) {
    var url = renderCardUrl(attrs, context);
    return url != null ? url : "[Inline card]";
  }

  String renderEmbedCard(CardAttrs attrs, RendererState context) {
    var url = attrs.url();
    if (url == null || url.isBlank()) {
      return "[Embed card]";
    }

    var resolvedUrl = resolveHref(url, context);
    var explicitTitle = attrs.title();
    if (explicitTitle != null && !explicitTitle.isBlank()) {
      return "[%s](%s)".formatted(MarkdownText.escapeLinkText(explicitTitle), resolvedUrl);
    }
    return "<%s>".formatted(resolvedUrl);
  }

  /** Resolves the href and label for a {@code link} mark applied to inline text. */
  ResolvedLink resolveLink(String href, String renderedLabel, RendererState context) {
    var resolvedHref = resolveHref(href, context);
    var fallbackLabel = (renderedLabel == null || renderedLabel.isBlank()) ? resolvedHref : renderedLabel;
    if (!shouldUseResolvedPageTitle(fallbackLabel, href, resolvedHref)) {
      return new ResolvedLink(resolvedHref, fallbackLabel);
    }
    return new ResolvedLink(resolvedHref, resolveInternalPageTitle(href, fallbackLabel, context));
  }

  private String renderCardUrl(CardAttrs attrs, RendererState context) {
    var url = attrs.url();
    if (url == null || url.isBlank()) {
      return null;
    }

    var resolvedUrl = resolveHref(url, context);
    var label = resolveCardLabel(attrs, url, resolvedUrl, context);
    return "[%s](%s)".formatted(MarkdownText.escapeLinkText(label), resolvedUrl);
  }

  private String resolveCardLabel(
      CardAttrs attrs, String originalUrl, String resolvedUrl, RendererState context) {
    var explicitTitle = attrs.title();
    if (explicitTitle != null && !explicitTitle.isBlank()) {
      return explicitTitle;
    }
    return resolveInternalPageTitle(originalUrl, resolvedUrl, context);
  }

  private String resolveHref(String href, RendererState context) {
    if (context.linkResolver() == null || context.currentPageId() == null) {
      return href;
    }
    var targetPageId = ConfluenceSupport.pageId(href);
    if (targetPageId != null) {
      return context.linkResolver().resolve(context.currentPageId(), targetPageId).orElse(href);
    }
    return href;
  }

  private boolean shouldUseResolvedPageTitle(String label, String originalHref, String resolvedHref) {
    if (label == null) {
      return true;
    }
    var normalizedLabel = label.strip();
    if (normalizedLabel.isEmpty()) {
      return true;
    }
    return Objects.equals(normalizedLabel, originalHref != null ? originalHref.strip() : "")
        || Objects.equals(normalizedLabel, resolvedHref != null ? resolvedHref.strip() : "");
  }

  private String resolveInternalPageTitle(String href, String fallbackLabel, RendererState context) {
    var targetPageId = ConfluenceSupport.pageId(href);
    if (targetPageId == null || context.pageTitleResolver() == null) {
      return fallbackLabel;
    }
    return context
        .pageTitleResolver()
        .resolve(targetPageId)
        .filter(s -> !s.isBlank())
        .orElse(fallbackLabel);
  }
}
