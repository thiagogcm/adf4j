package dev.nthings.adf4j.options;

import dev.nthings.adf4j.ast.AdfExtension;

/// Custom Markdown for an extension. REPLACE owns the whole node; HEADER keeps its body; DEFER
/// tries the next renderer, then the built-in output. A thrown exception yields CALLBACK_FAILED.
@FunctionalInterface
public interface ExtensionRenderer {
  ExtensionOutput render(AdfExtension extension);
}
