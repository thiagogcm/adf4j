package dev.nthings.adf4j.extension;

import java.util.Optional;

/**
 * Renders a custom ADF extension (macro) to Markdown, or returns {@link Optional#empty()} to defer.
 * Register via {@link dev.nthings.adf4j.options.MarkdownOptions#withExtensionRenderers}; renderers are
 * consulted in order, before the built-in Confluence macros, so one can add or override an extension.
 */
@FunctionalInterface
public interface ExtensionRenderer {

  Optional<String> render(ExtensionContext extension);
}
