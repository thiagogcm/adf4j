package dev.nthings.adf4j.extension;

import java.util.Optional;

/**
 * Renders a custom ADF extension (macro) to Markdown, or returns {@link Optional#empty()} to defer.
 * Register via {@link dev.nthings.adf4j.options.MarkdownOptions#withExtensionRenderers}; renderers are
 * consulted in order, before the built-in Confluence macros, so one can add or override an extension.
 *
 * <p>The returned string is emitted verbatim and is not escaped/sanitized — escape untrusted params
 * yourself. A {@code RuntimeException} from {@link #render} is logged and the built-in handling is
 * used instead.
 */
@FunctionalInterface
public interface ExtensionRenderer {

  Optional<String> render(ExtensionContext extension);
}
