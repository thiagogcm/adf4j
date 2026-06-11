package dev.nthings.adf4j.options;

/**
 * Renders a custom ADF extension (macro) to Markdown, or returns {@code null} to defer — the same
 * decline convention as the resolver hooks. An empty string is a valid answer that suppresses the
 * extension's output. Register via {@link MarkdownOptions#withExtensionRenderers}; renderers are
 * consulted in order, before the built-in Confluence macros, so one can add or override an extension.
 *
 * <p>The returned string is emitted verbatim and is not escaped/sanitized — escape untrusted params
 * yourself. A {@code RuntimeException} from {@link #render} is logged and treated as a defer.
 */
@FunctionalInterface
public interface ExtensionRenderer {

  String render(ExtensionContext extension);
}
