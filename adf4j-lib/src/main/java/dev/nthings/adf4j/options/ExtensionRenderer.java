package dev.nthings.adf4j.options;

import dev.nthings.adf4j.ast.AdfExtension;
import org.jspecify.annotations.Nullable;

/// Renders a custom ADF extension (macro) to Markdown, or returns `null` to defer (the same
/// decline convention as the resolver hooks). An empty string is a valid answer that suppresses the
/// extension's output. Register via {@link MarkdownOptions#withExtensionRenderers}; renderers are
/// consulted in order, before the built-in Confluence macros, so one can add or override an
/// extension.
///
/// The returned string is emitted verbatim and is not escaped/sanitized, so escape untrusted params
/// yourself. A `RuntimeException` from {@link #render} is logged and treated as a defer.
///
/// `macroParams()` holds the conventional `parameters.macroParams` values most Confluence macros
/// use. `parameters()` is the node's whole `parameters` envelope: some extensions (e.g. the modern
/// chart app, editor-migration macros) keep their data outside `macroParams`, and this is the only
/// place it surfaces. For a bodied extension this renders the header line only; the engine still
/// renders the body blocks.
@FunctionalInterface
public interface ExtensionRenderer {

  @Nullable String render(AdfExtension extension);
}
