package dev.nthings.adf4j.options;

import java.util.List;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.extension.ExtensionRenderer;

/**
 * Immutable configuration for an ADF-to-Markdown conversion. {@code imageSizeAttributes} emits the
 * non-GFM {@code {width= height=}} image suffix (off by default; the library targets GFM).
 * {@code tableFallback} selects how a GFM-safe table without an all-header first row is rendered
 * (first row promoted to a native pipe-table header by default). {@code mediaResolver} is an optional
 * hook that turns file media (which carries ids, not a URL) into a concrete link; {@code null} keeps
 * the {@code media:} placeholder. {@code htmlVisualMarks} preserves visual-only marks
 * (textColor/backgroundColor/border/fontSize) as an inline {@code <span style>} instead of dropping
 * them (off by default). {@code extensionRenderers} are optional hooks for rendering custom
 * extensions (macros), consulted before the built-in Confluence macros (empty by default).
 */
public record MarkdownOptions(
    UnknownNodePolicy unknownNodePolicy,
    ConfluenceRenderContext context,
    boolean imageSizeAttributes,
    TableFallback tableFallback,
    MediaResolver mediaResolver,
    boolean htmlVisualMarks,
    List<ExtensionRenderer> extensionRenderers) {

  public MarkdownOptions {
    unknownNodePolicy = unknownNodePolicy == null ? UnknownNodePolicy.PLACEHOLDER : unknownNodePolicy;
    context = context == null ? ConfluenceRenderContext.empty() : context;
    tableFallback = tableFallback == null ? TableFallback.GFM_PROMOTE_FIRST_ROW : tableFallback;
    extensionRenderers = extensionRenderers == null ? List.of() : List.copyOf(extensionRenderers);
    // mediaResolver intentionally nullable: null means "use the default media: placeholder".
  }

  public static MarkdownOptions defaults() {
    return new MarkdownOptions(
        UnknownNodePolicy.PLACEHOLDER,
        ConfluenceRenderContext.empty(),
        false,
        TableFallback.GFM_PROMOTE_FIRST_ROW,
        null,
        false,
        List.of());
  }

  public MarkdownOptions withUnknownNodePolicy(UnknownNodePolicy policy) {
    return new MarkdownOptions(
        policy, context, imageSizeAttributes, tableFallback, mediaResolver, htmlVisualMarks, extensionRenderers);
  }

  public MarkdownOptions withContext(ConfluenceRenderContext renderContext) {
    return new MarkdownOptions(
        unknownNodePolicy, renderContext, imageSizeAttributes, tableFallback, mediaResolver, htmlVisualMarks, extensionRenderers);
  }

  public MarkdownOptions withImageSizeAttributes(boolean enabled) {
    return new MarkdownOptions(
        unknownNodePolicy, context, enabled, tableFallback, mediaResolver, htmlVisualMarks, extensionRenderers);
  }

  public MarkdownOptions withTableFallback(TableFallback fallback) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, fallback, mediaResolver, htmlVisualMarks, extensionRenderers);
  }

  public MarkdownOptions withMediaResolver(MediaResolver resolver) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, tableFallback, resolver, htmlVisualMarks, extensionRenderers);
  }

  public MarkdownOptions withHtmlVisualMarks(boolean enabled) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, tableFallback, mediaResolver, enabled, extensionRenderers);
  }

  public MarkdownOptions withExtensionRenderers(List<ExtensionRenderer> renderers) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, tableFallback, mediaResolver, htmlVisualMarks, renderers);
  }
}
