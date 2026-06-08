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
 * {@code attachmentResolver} turns a resolved Confluence {@code attachment:} reference into a concrete
 * link; {@code null} keeps the {@code attachment:<fileId>} placeholder. {@code pageLinkResolver}
 * rewrites inter-page links/cards to caller-supplied destinations by page node id; {@code null} keeps
 * the original href. {@code pageTreeResolver} expands a {@code pagetree} macro into an indented list
 * of its descendant pages; {@code null} (or a resolver that declines) keeps the {@code {{pagetree}}}
 * placeholder token. {@code collapseHardBreaks} renders a hard break (Shift+Enter) as a soft break (a
 * plain newline) instead of the two-trailing-space GFM hard break, so the output has no trailing
 * whitespace and segments reflow into one paragraph (off by default). {@code documentTitle}, when set,
 * prepends the value as a level-1 ({@code # }) heading above the body ({@code null}/blank emits nothing);
 * it is render-only (not reflected in {@code ContentMetadata}) and not de-duplicated against an existing
 * leading heading. {@code escapeParentheses} backslash-escapes literal {@code (} and {@code )} in
 * rendered text and image alt text; off by default, since the escapes are inert noise outside a link
 * destination.
 *
 * <p>Construct via {@link #defaults()} plus the {@code withX(...)} withers, or via {@link #builder()};
 * both are forward-compatible. Avoid {@code new MarkdownOptions(...)}: the canonical constructor is not
 * a stable API — its parameter list grows whenever an option is added.
 */
public record MarkdownOptions(
    UnknownNodePolicy unknownNodePolicy,
    ConfluenceRenderContext context,
    boolean imageSizeAttributes,
    TableFallback tableFallback,
    MediaResolver mediaResolver,
    boolean htmlVisualMarks,
    List<ExtensionRenderer> extensionRenderers,
    AttachmentResolver attachmentResolver,
    PageLinkResolver pageLinkResolver,
    PageTreeResolver pageTreeResolver,
    boolean collapseHardBreaks,
    String documentTitle,
    boolean escapeParentheses) {

  public MarkdownOptions {
    unknownNodePolicy = unknownNodePolicy == null ? UnknownNodePolicy.PLACEHOLDER : unknownNodePolicy;
    context = context == null ? ConfluenceRenderContext.empty() : context;
    tableFallback = tableFallback == null ? TableFallback.GFM_PROMOTE_FIRST_ROW : tableFallback;
    extensionRenderers = extensionRenderers == null ? List.of() : List.copyOf(extensionRenderers);
    // mediaResolver/attachmentResolver/pageLinkResolver/pageTreeResolver/documentTitle intentionally
    // nullable: null means "use the built-in placeholder/href/token" (resolvers) or "no title".
  }

  public static MarkdownOptions defaults() {
    return new MarkdownOptions(
        UnknownNodePolicy.PLACEHOLDER,
        ConfluenceRenderContext.empty(),
        false,
        TableFallback.GFM_PROMOTE_FIRST_ROW,
        null,
        false,
        List.of(),
        null,
        null,
        null,
        false,
        null,
        false);
  }

  public MarkdownOptions withUnknownNodePolicy(UnknownNodePolicy policy) {
    return new MarkdownOptions(
        policy, context, imageSizeAttributes, tableFallback, mediaResolver, htmlVisualMarks,
        extensionRenderers, attachmentResolver, pageLinkResolver, pageTreeResolver, collapseHardBreaks,
        documentTitle, escapeParentheses);
  }

  public MarkdownOptions withContext(ConfluenceRenderContext renderContext) {
    return new MarkdownOptions(
        unknownNodePolicy, renderContext, imageSizeAttributes, tableFallback, mediaResolver,
        htmlVisualMarks, extensionRenderers, attachmentResolver, pageLinkResolver, pageTreeResolver,
        collapseHardBreaks, documentTitle, escapeParentheses);
  }

  public MarkdownOptions withImageSizeAttributes(boolean enabled) {
    return new MarkdownOptions(
        unknownNodePolicy, context, enabled, tableFallback, mediaResolver, htmlVisualMarks,
        extensionRenderers, attachmentResolver, pageLinkResolver, pageTreeResolver, collapseHardBreaks,
        documentTitle, escapeParentheses);
  }

  public MarkdownOptions withTableFallback(TableFallback fallback) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, fallback, mediaResolver, htmlVisualMarks,
        extensionRenderers, attachmentResolver, pageLinkResolver, pageTreeResolver, collapseHardBreaks,
        documentTitle, escapeParentheses);
  }

  /** Sets the media resolver; {@code null} clears it (the default {@code media:} placeholder path). */
  public MarkdownOptions withMediaResolver(MediaResolver resolver) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, tableFallback, resolver, htmlVisualMarks,
        extensionRenderers, attachmentResolver, pageLinkResolver, pageTreeResolver, collapseHardBreaks,
        documentTitle, escapeParentheses);
  }

  public MarkdownOptions withHtmlVisualMarks(boolean enabled) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, tableFallback, mediaResolver, enabled,
        extensionRenderers, attachmentResolver, pageLinkResolver, pageTreeResolver, collapseHardBreaks,
        documentTitle, escapeParentheses);
  }

  public MarkdownOptions withExtensionRenderers(List<ExtensionRenderer> renderers) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, tableFallback, mediaResolver, htmlVisualMarks,
        renderers, attachmentResolver, pageLinkResolver, pageTreeResolver, collapseHardBreaks,
        documentTitle, escapeParentheses);
  }

  /** Sets the attachment resolver; {@code null} clears it (the default {@code attachment:} path). */
  public MarkdownOptions withAttachmentResolver(AttachmentResolver resolver) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, tableFallback, mediaResolver, htmlVisualMarks,
        extensionRenderers, resolver, pageLinkResolver, pageTreeResolver, collapseHardBreaks,
        documentTitle, escapeParentheses);
  }

  /** Sets the page-link resolver; {@code null} clears it (links keep their original href). */
  public MarkdownOptions withPageLinkResolver(PageLinkResolver resolver) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, tableFallback, mediaResolver, htmlVisualMarks,
        extensionRenderers, attachmentResolver, resolver, pageTreeResolver, collapseHardBreaks,
        documentTitle, escapeParentheses);
  }

  /** Sets the page-tree resolver; {@code null} clears it (pagetree macros keep the {@code {{pagetree}}} token). */
  public MarkdownOptions withPageTreeResolver(PageTreeResolver resolver) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, tableFallback, mediaResolver, htmlVisualMarks,
        extensionRenderers, attachmentResolver, pageLinkResolver, resolver, collapseHardBreaks,
        documentTitle, escapeParentheses);
  }

  /** Renders hard breaks as soft breaks (a plain newline), dropping the two-space GFM hard break. */
  public MarkdownOptions withCollapseHardBreaks(boolean enabled) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, tableFallback, mediaResolver, htmlVisualMarks,
        extensionRenderers, attachmentResolver, pageLinkResolver, pageTreeResolver, enabled,
        documentTitle, escapeParentheses);
  }

  /** Sets a level-1 title heading prepended to the output; {@code null}/blank emits no title. */
  public MarkdownOptions withDocumentTitle(String title) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, tableFallback, mediaResolver, htmlVisualMarks,
        extensionRenderers, attachmentResolver, pageLinkResolver, pageTreeResolver, collapseHardBreaks,
        title, escapeParentheses);
  }

  /** Backslash-escapes literal {@code (} and {@code )} in rendered text and image alt text. */
  public MarkdownOptions withEscapeParentheses(boolean enabled) {
    return new MarkdownOptions(
        unknownNodePolicy, context, imageSizeAttributes, tableFallback, mediaResolver, htmlVisualMarks,
        extensionRenderers, attachmentResolver, pageLinkResolver, pageTreeResolver, collapseHardBreaks,
        documentTitle, enabled);
  }

  /** A new {@link Builder} whose unset fields take the same defaults as {@link #defaults()}. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent, forward-compatible builder for {@link MarkdownOptions}. Unset fields take the
   * {@link #defaults()} values; passing {@code null} to a resolver setter leaves that resolver unset.
   */
  public static final class Builder {

    private UnknownNodePolicy unknownNodePolicy;
    private ConfluenceRenderContext context;
    private boolean imageSizeAttributes;
    private TableFallback tableFallback;
    private MediaResolver mediaResolver;
    private boolean htmlVisualMarks;
    private List<ExtensionRenderer> extensionRenderers;
    private AttachmentResolver attachmentResolver;
    private PageLinkResolver pageLinkResolver;
    private PageTreeResolver pageTreeResolver;
    private boolean collapseHardBreaks;
    private String documentTitle;
    private boolean escapeParentheses;

    private Builder() {
    }

    public Builder unknownNodePolicy(UnknownNodePolicy policy) {
      this.unknownNodePolicy = policy;
      return this;
    }

    public Builder context(ConfluenceRenderContext renderContext) {
      this.context = renderContext;
      return this;
    }

    public Builder imageSizeAttributes(boolean enabled) {
      this.imageSizeAttributes = enabled;
      return this;
    }

    public Builder tableFallback(TableFallback fallback) {
      this.tableFallback = fallback;
      return this;
    }

    public Builder mediaResolver(MediaResolver resolver) {
      this.mediaResolver = resolver;
      return this;
    }

    public Builder htmlVisualMarks(boolean enabled) {
      this.htmlVisualMarks = enabled;
      return this;
    }

    public Builder extensionRenderers(List<ExtensionRenderer> renderers) {
      this.extensionRenderers = renderers;
      return this;
    }

    public Builder attachmentResolver(AttachmentResolver resolver) {
      this.attachmentResolver = resolver;
      return this;
    }

    public Builder pageLinkResolver(PageLinkResolver resolver) {
      this.pageLinkResolver = resolver;
      return this;
    }

    public Builder pageTreeResolver(PageTreeResolver resolver) {
      this.pageTreeResolver = resolver;
      return this;
    }

    public Builder collapseHardBreaks(boolean enabled) {
      this.collapseHardBreaks = enabled;
      return this;
    }

    /** Sets a level-1 title heading prepended to the output; {@code null}/blank emits no title. */
    public Builder documentTitle(String title) {
      this.documentTitle = title;
      return this;
    }

    /** Backslash-escapes literal {@code (} and {@code )} in rendered text and image alt text. */
    public Builder escapeParentheses(boolean enabled) {
      this.escapeParentheses = enabled;
      return this;
    }

    public MarkdownOptions build() {
      return new MarkdownOptions(
          unknownNodePolicy, context, imageSizeAttributes, tableFallback, mediaResolver,
          htmlVisualMarks, extensionRenderers, attachmentResolver, pageLinkResolver,
          pageTreeResolver, collapseHardBreaks, documentTitle, escapeParentheses);
    }
  }
}
