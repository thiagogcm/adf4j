package dev.nthings.adf4j.options;

import dev.nthings.adf4j.metadata.AttachmentReference;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/// Immutable configuration for an ADF-to-Markdown conversion: {@link #defaults()} plus the
/// `withX(...)` withers.
///
/// `imageSizeAttributes` emits the non-GFM `{width= height=}` image suffix (off by
/// default; the library targets GFM). `tableFallback` selects how a GFM-safe table without an
/// all-header first row is rendered (first row promoted to a native pipe-table header by default).
/// `attachments` is the page's attachment inventory, in document order: `null` means it is unknown,
/// while a list, even an empty one, is authoritative, so an `attachments` macro lists exactly those
/// entries. An entry is identified by its `fileId`; the first entry with a given `fileId` wins, as
/// does the first with a given title when a macro looks one up by name, and the title lookup is
/// case-insensitive and ignores surrounding whitespace. An entry needs no title, but one with a
/// blank `fileId` is ignored. A `downloadUrl` is the default destination of the file's links.
/// `mediaResolver` is an optional hook that turns file media (which carries ids, not a URL)
/// into a concrete link; `null` keeps the `media:` placeholder. `htmlVisualMarks`
/// preserves visual-only marks (textColor/backgroundColor/border/fontSize) as an inline `<span
/// style>` instead of dropping them (off by default), except for a value that isn't a plain color
/// or size, which is untrusted input and stays dropped. `extensionRenderers` are optional hooks
/// for rendering custom extensions (macros), consulted before the built-in Confluence macros (empty
/// by default). `attachmentResolver` turns a resolved Confluence `attachment:` reference
/// into a concrete link; `null` keeps the `attachment:<fileId>` placeholder.
/// `pageLinkResolver` rewrites inter-page links/cards to caller-supplied destinations by page node
/// id; `null` keeps the original href. `pageTreeResolver` expands a `pagetree`
/// macro into an indented list of its descendant pages; an empty (non-null) list renders as
/// nothing,
/// while `null` (or no resolver) keeps the `{{pagetree}}` placeholder token.
/// `excerptResolver` expands an `excerpt-include` macro into caller-supplied Markdown;
/// `null` (or a declined lookup) keeps the macro's placeholder and records the reference on
/// `MarkdownResult.unresolved()`. `collapseHardBreaks` renders a hard break (Shift+Enter) as a
/// soft break (a plain newline) instead of the two-trailing-space GFM hard break, so the output has
/// no trailing whitespace and segments reflow into one paragraph (off by default).
/// `documentTitle`, when set, prepends the value as a level-1 (`# `) heading above the body
/// (`null`/blank emits nothing); it is emitted even when the body is empty, blank, or fails to
/// parse (a titled-but-empty document needs no synthetic ADF input), and is render-only (not
/// reflected in `ContentMetadata`), not de-duplicated against an existing leading heading.
/// `escapeParentheses` backslash-escapes literal `(` and `)` in rendered text and
/// image alt text; off by default, since the escapes are inert noise outside a link destination.
public final class MarkdownOptions {

  private static final MarkdownOptions DEFAULTS = new MarkdownOptions(new Copy());

  private final UnknownNodePolicy unknownNodePolicy;
  private final @Nullable List<AttachmentReference> attachments;
  private final boolean imageSizeAttributes;
  private final TableFallback tableFallback;
  private final @Nullable MediaResolver mediaResolver;
  private final boolean htmlVisualMarks;
  private final List<ExtensionRenderer> extensionRenderers;
  private final @Nullable AttachmentResolver attachmentResolver;
  private final @Nullable PageLinkResolver pageLinkResolver;
  private final @Nullable PageTreeResolver pageTreeResolver;
  private final @Nullable ExcerptResolver excerptResolver;
  private final boolean collapseHardBreaks;
  private final @Nullable String documentTitle;
  private final boolean escapeParentheses;

  private MarkdownOptions(Copy copy) {
    this.unknownNodePolicy = copy.unknownNodePolicy;
    this.attachments = copy.attachments;
    this.imageSizeAttributes = copy.imageSizeAttributes;
    this.tableFallback = copy.tableFallback;
    this.mediaResolver = copy.mediaResolver;
    this.htmlVisualMarks = copy.htmlVisualMarks;
    this.extensionRenderers = copy.extensionRenderers;
    this.attachmentResolver = copy.attachmentResolver;
    this.pageLinkResolver = copy.pageLinkResolver;
    this.pageTreeResolver = copy.pageTreeResolver;
    this.excerptResolver = copy.excerptResolver;
    this.collapseHardBreaks = copy.collapseHardBreaks;
    this.documentTitle = copy.documentTitle;
    this.escapeParentheses = copy.escapeParentheses;
  }

  /// GFM-targeting options with every hook unset and every toggle off (the safe baseline).
  public static MarkdownOptions defaults() {
    return DEFAULTS;
  }

  public UnknownNodePolicy unknownNodePolicy() {
    return unknownNodePolicy;
  }

  /// The page's attachment inventory in document order, or `null` when it is unknown.
  public @Nullable List<AttachmentReference> attachments() {
    return attachments;
  }

  public boolean imageSizeAttributes() {
    return imageSizeAttributes;
  }

  public TableFallback tableFallback() {
    return tableFallback;
  }

  public @Nullable MediaResolver mediaResolver() {
    return mediaResolver;
  }

  public boolean htmlVisualMarks() {
    return htmlVisualMarks;
  }

  public List<ExtensionRenderer> extensionRenderers() {
    return extensionRenderers;
  }

  public @Nullable AttachmentResolver attachmentResolver() {
    return attachmentResolver;
  }

  public @Nullable PageLinkResolver pageLinkResolver() {
    return pageLinkResolver;
  }

  public @Nullable PageTreeResolver pageTreeResolver() {
    return pageTreeResolver;
  }

  public @Nullable ExcerptResolver excerptResolver() {
    return excerptResolver;
  }

  public boolean collapseHardBreaks() {
    return collapseHardBreaks;
  }

  public @Nullable String documentTitle() {
    return documentTitle;
  }

  public boolean escapeParentheses() {
    return escapeParentheses;
  }

  /// Sets the unknown-node policy.
  public MarkdownOptions withUnknownNodePolicy(UnknownNodePolicy policy) {
    Objects.requireNonNull(policy, "policy");
    return with(copy -> copy.unknownNodePolicy = policy);
  }

  /// Sets the attachment inventory (defensively copied, keeping its order); `null` means it is
  /// unknown, and a list, even an empty one, is authoritative.
  public MarkdownOptions withAttachments(@Nullable List<AttachmentReference> attachments) {
    var copied = attachments == null ? null : List.copyOf(attachments);
    return with(copy -> copy.attachments = copied);
  }

  public MarkdownOptions withImageSizeAttributes(boolean enabled) {
    return with(copy -> copy.imageSizeAttributes = enabled);
  }

  /// Sets the table fallback.
  public MarkdownOptions withTableFallback(TableFallback fallback) {
    Objects.requireNonNull(fallback, "fallback");
    return with(copy -> copy.tableFallback = fallback);
  }

  /// Sets the media resolver; `null` clears it (the default `media:` placeholder path).
  public MarkdownOptions withMediaResolver(@Nullable MediaResolver resolver) {
    return with(copy -> copy.mediaResolver = resolver);
  }

  public MarkdownOptions withHtmlVisualMarks(boolean enabled) {
    return with(copy -> copy.htmlVisualMarks = enabled);
  }

  /// Sets the ordered extension renderers (defensively copied). The list must not contain `null`
  /// elements.
  public MarkdownOptions withExtensionRenderers(List<ExtensionRenderer> renderers) {
    Objects.requireNonNull(renderers, "renderers");
    renderers.forEach(
        renderer ->
            Objects.requireNonNull(renderer, "extensionRenderers must not contain null elements"));
    var copied = List.copyOf(renderers);
    return with(copy -> copy.extensionRenderers = copied);
  }

  /// Sets the attachment resolver; `null` clears it (the default `attachment:` path).
  public MarkdownOptions withAttachmentResolver(@Nullable AttachmentResolver resolver) {
    return with(copy -> copy.attachmentResolver = resolver);
  }

  /// Sets the page-link resolver; `null` clears it (links keep their original href).
  public MarkdownOptions withPageLinkResolver(@Nullable PageLinkResolver resolver) {
    return with(copy -> copy.pageLinkResolver = resolver);
  }

  /// Sets the page-tree resolver; `null` clears it (pagetree macros keep the `{{pagetree}}` token).
  public MarkdownOptions withPageTreeResolver(@Nullable PageTreeResolver resolver) {
    return with(copy -> copy.pageTreeResolver = resolver);
  }

  /// Sets the excerpt resolver; `null` clears it (excerpt-include macros keep their placeholder).
  public MarkdownOptions withExcerptResolver(@Nullable ExcerptResolver resolver) {
    return with(copy -> copy.excerptResolver = resolver);
  }

  /// Renders hard breaks as soft breaks (a plain newline), dropping the two-space GFM hard break.
  public MarkdownOptions withCollapseHardBreaks(boolean enabled) {
    return with(copy -> copy.collapseHardBreaks = enabled);
  }

  /// Sets a level-1 title heading prepended to the output; `null`/blank emits no title.
  public MarkdownOptions withDocumentTitle(@Nullable String title) {
    return with(copy -> copy.documentTitle = title);
  }

  /// Backslash-escapes literal `(` and `)` in rendered text and image alt text.
  public MarkdownOptions withEscapeParentheses(boolean enabled) {
    return with(copy -> copy.escapeParentheses = enabled);
  }

  private MarkdownOptions with(Consumer<Copy> change) {
    var copy = new Copy(this);
    change.accept(copy);
    return new MarkdownOptions(copy);
  }

  // The fields of an instance being derived, starting at the defaults.
  private static final class Copy {

    UnknownNodePolicy unknownNodePolicy = UnknownNodePolicy.PLACEHOLDER;
    @Nullable List<AttachmentReference> attachments;
    boolean imageSizeAttributes;
    TableFallback tableFallback = TableFallback.GFM_PROMOTE_FIRST_ROW;
    @Nullable MediaResolver mediaResolver;
    boolean htmlVisualMarks;
    List<ExtensionRenderer> extensionRenderers = List.of();
    @Nullable AttachmentResolver attachmentResolver;
    @Nullable PageLinkResolver pageLinkResolver;
    @Nullable PageTreeResolver pageTreeResolver;
    @Nullable ExcerptResolver excerptResolver;
    boolean collapseHardBreaks;
    @Nullable String documentTitle;
    boolean escapeParentheses;

    Copy() {}

    Copy(MarkdownOptions options) {
      this.unknownNodePolicy = options.unknownNodePolicy;
      this.attachments = options.attachments;
      this.imageSizeAttributes = options.imageSizeAttributes;
      this.tableFallback = options.tableFallback;
      this.mediaResolver = options.mediaResolver;
      this.htmlVisualMarks = options.htmlVisualMarks;
      this.extensionRenderers = options.extensionRenderers;
      this.attachmentResolver = options.attachmentResolver;
      this.pageLinkResolver = options.pageLinkResolver;
      this.pageTreeResolver = options.pageTreeResolver;
      this.excerptResolver = options.excerptResolver;
      this.collapseHardBreaks = options.collapseHardBreaks;
      this.documentTitle = options.documentTitle;
      this.escapeParentheses = options.escapeParentheses;
    }
  }
}
