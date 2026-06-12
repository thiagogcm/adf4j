package dev.nthings.adf4j.options;

import java.util.List;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;

/**
 * Immutable configuration for an ADF-to-Markdown conversion. Construct via {@link #defaults()} plus
 * the {@code withX(...)} withers, or via {@link #builder()}; an existing instance can be varied with
 * {@link #toBuilder()}.
 *
 * <p>{@code imageSizeAttributes} emits the non-GFM {@code {width= height=}} image suffix (off by
 * default; the library targets GFM). {@code tableFallback} selects how a GFM-safe table without an
 * all-header first row is rendered (first row promoted to a native pipe-table header by default).
 * {@code mediaResolver} is an optional hook that turns file media (which carries ids, not a URL) into
 * a concrete link; {@code null} keeps the {@code media:} placeholder. {@code htmlVisualMarks}
 * preserves visual-only marks (textColor/backgroundColor/border/fontSize) as an inline
 * {@code <span style>} instead of dropping them (off by default). {@code extensionRenderers} are
 * optional hooks for rendering custom extensions (macros), consulted before the built-in Confluence
 * macros (empty by default). {@code attachmentResolver} turns a resolved Confluence
 * {@code attachment:} reference into a concrete link; {@code null} keeps the
 * {@code attachment:<fileId>} placeholder. {@code pageLinkResolver} rewrites inter-page links/cards
 * to caller-supplied destinations by page node id; {@code null} keeps the original href.
 * {@code pageTreeResolver} expands a {@code pagetree} macro into an indented list of its descendant
 * pages; an empty (non-null) list renders as nothing, while {@code null} (or no resolver) keeps the
 * {@code {{pagetree}}} placeholder token. {@code excerptResolver} expands an {@code excerpt-include}
 * macro into caller-supplied Markdown; {@code null} (or a declined lookup) keeps the macro's
 * placeholder and records the reference on {@code MarkdownResult.unresolved()}.
 * {@code collapseHardBreaks} renders a hard break
 * (Shift+Enter) as a soft break (a plain newline) instead of the two-trailing-space GFM hard break,
 * so the output has no trailing whitespace and segments reflow into one paragraph (off by default).
 * {@code documentTitle}, when set, prepends the value as a level-1 ({@code # }) heading above the
 * body ({@code null}/blank emits nothing); it is emitted even when the body is empty, blank, or fails
 * to parse — a titled-but-empty document needs no synthetic ADF input — and is render-only (not
 * reflected in {@code ContentMetadata}), not de-duplicated against an existing leading heading.
 * {@code escapeParentheses} backslash-escapes literal {@code (} and {@code )} in rendered text and
 * image alt text; off by default, since the escapes are inert noise outside a link destination.
 */
public final class MarkdownOptions {

  private final UnknownNodePolicy unknownNodePolicy;
  private final ConfluenceRenderContext confluenceContext;
  private final boolean imageSizeAttributes;
  private final TableFallback tableFallback;
  private final MediaResolver mediaResolver;
  private final boolean htmlVisualMarks;
  private final List<ExtensionRenderer> extensionRenderers;
  private final AttachmentResolver attachmentResolver;
  private final PageLinkResolver pageLinkResolver;
  private final PageTreeResolver pageTreeResolver;
  private final ExcerptResolver excerptResolver;
  private final boolean collapseHardBreaks;
  private final String documentTitle;
  private final boolean escapeParentheses;

  private MarkdownOptions(Builder builder) {
    this.unknownNodePolicy =
        builder.unknownNodePolicy == null ? UnknownNodePolicy.PLACEHOLDER : builder.unknownNodePolicy;
    this.confluenceContext =
        builder.confluenceContext == null ? ConfluenceRenderContext.empty() : builder.confluenceContext;
    this.imageSizeAttributes = builder.imageSizeAttributes;
    this.tableFallback =
        builder.tableFallback == null ? TableFallback.GFM_PROMOTE_FIRST_ROW : builder.tableFallback;
    this.mediaResolver = builder.mediaResolver;
    this.htmlVisualMarks = builder.htmlVisualMarks;
    this.extensionRenderers = copyOfRenderers(builder.extensionRenderers);
    this.attachmentResolver = builder.attachmentResolver;
    this.pageLinkResolver = builder.pageLinkResolver;
    this.pageTreeResolver = builder.pageTreeResolver;
    this.excerptResolver = builder.excerptResolver;
    this.collapseHardBreaks = builder.collapseHardBreaks;
    this.documentTitle = builder.documentTitle;
    this.escapeParentheses = builder.escapeParentheses;
  }

  private static List<ExtensionRenderer> copyOfRenderers(List<ExtensionRenderer> renderers) {
    if (renderers == null || renderers.isEmpty()) {
      return List.of();
    }
    for (var renderer : renderers) {
      if (renderer == null) {
        throw new NullPointerException("extensionRenderers must not contain null elements");
      }
    }
    return List.copyOf(renderers);
  }

  public static MarkdownOptions defaults() {
    return builder().build();
  }

  /** A new {@link Builder} whose unset fields take the same defaults as {@link #defaults()}. */
  public static Builder builder() {
    return new Builder();
  }

  /** A {@link Builder} pre-populated with this instance's values. */
  public Builder toBuilder() {
    var builder = new Builder();
    builder.unknownNodePolicy = unknownNodePolicy;
    builder.confluenceContext = confluenceContext;
    builder.imageSizeAttributes = imageSizeAttributes;
    builder.tableFallback = tableFallback;
    builder.mediaResolver = mediaResolver;
    builder.htmlVisualMarks = htmlVisualMarks;
    builder.extensionRenderers = extensionRenderers;
    builder.attachmentResolver = attachmentResolver;
    builder.pageLinkResolver = pageLinkResolver;
    builder.pageTreeResolver = pageTreeResolver;
    builder.excerptResolver = excerptResolver;
    builder.collapseHardBreaks = collapseHardBreaks;
    builder.documentTitle = documentTitle;
    builder.escapeParentheses = escapeParentheses;
    return builder;
  }

  public UnknownNodePolicy unknownNodePolicy() {
    return unknownNodePolicy;
  }

  public ConfluenceRenderContext confluenceContext() {
    return confluenceContext;
  }

  public boolean imageSizeAttributes() {
    return imageSizeAttributes;
  }

  public TableFallback tableFallback() {
    return tableFallback;
  }

  public MediaResolver mediaResolver() {
    return mediaResolver;
  }

  public boolean htmlVisualMarks() {
    return htmlVisualMarks;
  }

  public List<ExtensionRenderer> extensionRenderers() {
    return extensionRenderers;
  }

  public AttachmentResolver attachmentResolver() {
    return attachmentResolver;
  }

  public PageLinkResolver pageLinkResolver() {
    return pageLinkResolver;
  }

  public PageTreeResolver pageTreeResolver() {
    return pageTreeResolver;
  }

  public ExcerptResolver excerptResolver() {
    return excerptResolver;
  }

  public boolean collapseHardBreaks() {
    return collapseHardBreaks;
  }

  public String documentTitle() {
    return documentTitle;
  }

  public boolean escapeParentheses() {
    return escapeParentheses;
  }

  public MarkdownOptions withUnknownNodePolicy(UnknownNodePolicy policy) {
    return toBuilder().unknownNodePolicy(policy).build();
  }

  /** Sets the Confluence context; {@code null} resets to {@link ConfluenceRenderContext#empty()}. */
  public MarkdownOptions withConfluenceContext(ConfluenceRenderContext context) {
    return toBuilder().confluenceContext(context).build();
  }

  public MarkdownOptions withImageSizeAttributes(boolean enabled) {
    return toBuilder().imageSizeAttributes(enabled).build();
  }

  public MarkdownOptions withTableFallback(TableFallback fallback) {
    return toBuilder().tableFallback(fallback).build();
  }

  /** Sets the media resolver; {@code null} clears it (the default {@code media:} placeholder path). */
  public MarkdownOptions withMediaResolver(MediaResolver resolver) {
    return toBuilder().mediaResolver(resolver).build();
  }

  public MarkdownOptions withHtmlVisualMarks(boolean enabled) {
    return toBuilder().htmlVisualMarks(enabled).build();
  }

  public MarkdownOptions withExtensionRenderers(List<ExtensionRenderer> renderers) {
    return toBuilder().extensionRenderers(renderers).build();
  }

  /** Sets the attachment resolver; {@code null} clears it (the default {@code attachment:} path). */
  public MarkdownOptions withAttachmentResolver(AttachmentResolver resolver) {
    return toBuilder().attachmentResolver(resolver).build();
  }

  /** Sets the page-link resolver; {@code null} clears it (links keep their original href). */
  public MarkdownOptions withPageLinkResolver(PageLinkResolver resolver) {
    return toBuilder().pageLinkResolver(resolver).build();
  }

  /** Sets the page-tree resolver; {@code null} clears it (pagetree macros keep the {@code {{pagetree}}} token). */
  public MarkdownOptions withPageTreeResolver(PageTreeResolver resolver) {
    return toBuilder().pageTreeResolver(resolver).build();
  }

  /** Sets the excerpt resolver; {@code null} clears it (excerpt-include macros keep their placeholder). */
  public MarkdownOptions withExcerptResolver(ExcerptResolver resolver) {
    return toBuilder().excerptResolver(resolver).build();
  }

  /** Renders hard breaks as soft breaks (a plain newline), dropping the two-space GFM hard break. */
  public MarkdownOptions withCollapseHardBreaks(boolean enabled) {
    return toBuilder().collapseHardBreaks(enabled).build();
  }

  /** Sets a level-1 title heading prepended to the output; {@code null}/blank emits no title. */
  public MarkdownOptions withDocumentTitle(String title) {
    return toBuilder().documentTitle(title).build();
  }

  /** Backslash-escapes literal {@code (} and {@code )} in rendered text and image alt text. */
  public MarkdownOptions withEscapeParentheses(boolean enabled) {
    return toBuilder().escapeParentheses(enabled).build();
  }

  /**
   * Fluent builder for {@link MarkdownOptions}. Unset fields take the {@link #defaults()} values;
   * passing {@code null} to a setter resets that field to its default.
   */
  public static final class Builder {

    private UnknownNodePolicy unknownNodePolicy;
    private ConfluenceRenderContext confluenceContext;
    private boolean imageSizeAttributes;
    private TableFallback tableFallback;
    private MediaResolver mediaResolver;
    private boolean htmlVisualMarks;
    private List<ExtensionRenderer> extensionRenderers;
    private AttachmentResolver attachmentResolver;
    private PageLinkResolver pageLinkResolver;
    private PageTreeResolver pageTreeResolver;
    private ExcerptResolver excerptResolver;
    private boolean collapseHardBreaks;
    private String documentTitle;
    private boolean escapeParentheses;

    private Builder() {
    }

    public Builder unknownNodePolicy(UnknownNodePolicy policy) {
      this.unknownNodePolicy = policy;
      return this;
    }

    public Builder confluenceContext(ConfluenceRenderContext context) {
      this.confluenceContext = context;
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

    public Builder excerptResolver(ExcerptResolver resolver) {
      this.excerptResolver = resolver;
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
      return new MarkdownOptions(this);
    }
  }
}
