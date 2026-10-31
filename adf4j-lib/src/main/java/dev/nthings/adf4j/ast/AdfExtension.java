package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// The fields shared by the extension nodes ({@link Extension}, {@link BodiedExtension},
/// {@link MultiBodiedExtension}, {@link InlineExtension}), read from their `attrs`.
public sealed interface AdfExtension
    permits Extension, BodiedExtension, MultiBodiedExtension, InlineExtension {

  /// The node's raw ADF attributes.
  Attributes attrs();

  /// The extension namespace, e.g. `com.atlassian.confluence.macro.core`.
  default @Nullable String extensionType() {
    return attrs().text("extensionType");
  }

  /// The extension name within its namespace, e.g. `toc`.
  default @Nullable String extensionKey() {
    return attrs().text("extensionKey");
  }

  /// The fallback text the editor stored for display.
  default @Nullable String text() {
    return attrs().text("text");
  }

  /// The full raw `parameters` envelope.
  default Attributes parameters() {
    return attrs().object("parameters");
  }

  /// The flattened `parameters.macroParams` values most Confluence macros use.
  default MacroParams macroParams() {
    return MacroParams.from(parameters().object("macroParams"));
  }
}
