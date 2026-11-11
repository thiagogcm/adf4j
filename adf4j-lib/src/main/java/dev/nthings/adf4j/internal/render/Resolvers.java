package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfExtension;
import dev.nthings.adf4j.internal.resource.CallbackTracker;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.options.ExtensionOutput;
import dev.nthings.adf4j.options.ResolverHooks;
import org.jspecify.annotations.Nullable;

/// Markdown-valued caller hooks. Resource resolution is renderer-neutral and lives in
/// {@link dev.nthings.adf4j.internal.resource.ResourceResolver}.
final class Resolvers {

  private final ResolverHooks hooks;
  private final CallbackTracker callbacks;

  Resolvers(ResolverHooks hooks, CallbackTracker callbacks) {
    this.hooks = hooks;
    this.callbacks = callbacks;
  }

  @Nullable String excerpt(ExcerptIncludeReference reference, String path) {
    var resolver = hooks.excerptResolver();
    return resolver == null
        ? null
        : callbacks.call("ExcerptResolver", path, () -> resolver.resolve(reference));
  }

  /// First non-deferred answer; `null` falls through to the built-in handler.
  @Nullable ExtensionOutput extension(AdfExtension node, String path) {
    for (var renderer : hooks.extensionRenderers()) {
      var rendered = callbacks.call("ExtensionRenderer", path, () -> renderer.render(node));
      if (rendered != null && rendered.mode() != ExtensionOutput.Mode.DEFER) {
        return rendered;
      }
    }
    return null;
  }
}
