package dev.nthings.adf4j.options;

import dev.nthings.adf4j.ast.AdfExtension;

/// Custom Markdown for an extension. Replace owns the whole node; header retains its body.
/// Defer tries the next renderer then the built-in behavior. Exceptions produce CALLBACK_FAILED.
@FunctionalInterface
public interface ExtensionRenderer {
  ExtensionOutput render(AdfExtension extension);
}
