package dev.nthings.adf4j.options;

import dev.nthings.adf4j.internal.config.ConversionConfig;
import dev.nthings.adf4j.metadata.AttachmentReference;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Immutable conversion configuration: shared unknown-node policy, Markdown policy, per-document
/// data, and Java callbacks.
public record ConversionOptions(
    UnknownNodePolicy unknownNodePolicy,
    MarkdownOptions markdown,
    DocumentContext context,
    ResolverHooks resolvers) {
  public ConversionOptions {
    Objects.requireNonNull(unknownNodePolicy, "unknownNodePolicy");
    Objects.requireNonNull(markdown, "markdown");
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(resolvers, "resolvers");
  }

  /// Reads the portable `{ unknownNodePolicy, markdown, context }` JSON shared with the CLI and
  /// WASM. Blank input means defaults; invalid input throws `AdfException` with one
  /// `INVALID_OPTIONS` diagnostic.
  public static ConversionOptions fromJson(String json) {
    return ConversionConfig.read(json);
  }

  public static ConversionOptions defaults() {
    return new ConversionOptions(
        UnknownNodePolicy.PLACEHOLDER,
        MarkdownOptions.defaults(),
        DocumentContext.defaults(),
        ResolverHooks.defaults());
  }

  public ConversionOptions withUnknownNodePolicy(UnknownNodePolicy value) {
    return new ConversionOptions(value, markdown, context, resolvers);
  }

  public ConversionOptions withMarkdown(MarkdownOptions value) {
    return new ConversionOptions(unknownNodePolicy, value, context, resolvers);
  }

  public ConversionOptions withContext(DocumentContext value) {
    return new ConversionOptions(unknownNodePolicy, markdown, value, resolvers);
  }

  public ConversionOptions withResolvers(ResolverHooks value) {
    return new ConversionOptions(unknownNodePolicy, markdown, context, value);
  }

  public ResourceResolvers resourceResolvers() {
    return resolvers.resources();
  }

  public ConversionOptions withResourceResolvers(ResourceResolvers value) {
    return withResolvers(resolvers.withResources(value));
  }

  public boolean imageSizeAttributes() {
    return markdown.imageSizeAttributes();
  }

  public ConversionOptions withImageSizeAttributes(boolean value) {
    return withMarkdown(markdown.withImageSizeAttributes(value));
  }

  public TableFallback tableFallback() {
    return markdown.tableFallback();
  }

  public ConversionOptions withTableFallback(TableFallback value) {
    return withMarkdown(markdown.withTableFallback(value));
  }

  public boolean htmlVisualMarks() {
    return markdown.htmlVisualMarks();
  }

  public ConversionOptions withHtmlVisualMarks(boolean value) {
    return withMarkdown(markdown.withHtmlVisualMarks(value));
  }

  public boolean collapseHardBreaks() {
    return markdown.collapseHardBreaks();
  }

  public ConversionOptions withCollapseHardBreaks(boolean value) {
    return withMarkdown(markdown.withCollapseHardBreaks(value));
  }

  public boolean escapeParentheses() {
    return markdown.escapeParentheses();
  }

  public ConversionOptions withEscapeParentheses(boolean value) {
    return withMarkdown(markdown.withEscapeParentheses(value));
  }

  public @Nullable List<AttachmentReference> attachments() {
    return context.attachments();
  }

  public ConversionOptions withAttachments(@Nullable List<AttachmentReference> value) {
    return withContext(context.withAttachments(value));
  }

  public @Nullable String documentTitle() {
    return context.title();
  }

  public ConversionOptions withDocumentTitle(@Nullable String value) {
    return withContext(context.withTitle(value));
  }

  public @Nullable MediaResolver mediaResolver() {
    return resolvers.mediaResolver();
  }

  public ConversionOptions withMediaResolver(@Nullable MediaResolver value) {
    return withResourceResolvers(resolvers.resources().withMediaResolver(value));
  }

  public @Nullable AttachmentResolver attachmentResolver() {
    return resolvers.attachmentResolver();
  }

  public ConversionOptions withAttachmentResolver(@Nullable AttachmentResolver value) {
    return withResourceResolvers(resolvers.resources().withAttachmentResolver(value));
  }

  public @Nullable FileResolver fileResolver() {
    return resolvers.fileResolver();
  }

  public ConversionOptions withFileResolver(@Nullable FileResolver value) {
    return withResourceResolvers(resolvers.resources().withFileResolver(value));
  }

  public @Nullable PageLinkResolver pageLinkResolver() {
    return resolvers.pageLinkResolver();
  }

  public ConversionOptions withPageLinkResolver(@Nullable PageLinkResolver value) {
    return withResourceResolvers(resolvers.resources().withPageLinkResolver(value));
  }

  public @Nullable PageTreeResolver pageTreeResolver() {
    return resolvers.pageTreeResolver();
  }

  public ConversionOptions withPageTreeResolver(@Nullable PageTreeResolver value) {
    return withResourceResolvers(resolvers.resources().withPageTreeResolver(value));
  }

  public @Nullable ExcerptContentResolver excerptContentResolver() {
    return resolvers.excerptContentResolver();
  }

  public ConversionOptions withExcerptContentResolver(@Nullable ExcerptContentResolver value) {
    return withResourceResolvers(resolvers.resources().withExcerptContentResolver(value));
  }

  public @Nullable ExcerptResolver excerptResolver() {
    return resolvers.excerptResolver();
  }

  public ConversionOptions withExcerptResolver(@Nullable ExcerptResolver value) {
    return withResolvers(resolvers.withExcerptResolver(value));
  }

  public List<ExtensionRenderer> extensionRenderers() {
    return resolvers.extensionRenderers();
  }

  public ConversionOptions withExtensionRenderers(List<ExtensionRenderer> value) {
    return withResolvers(resolvers.withExtensionRenderers(value));
  }
}
