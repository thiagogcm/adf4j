package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Unsupported macros rendered as `[Extension: …]` placeholders, whose content is lost.
/// Fresh per render and single-threaded, so no synchronization.
final class MacroDiagnostics {

  private final List<String> paths = new ArrayList<>();
  private final Set<String> unsupportedMacros = new LinkedHashSet<>();

  /// Records one unsupported macro; true if its label is new to this render.
  boolean recordUnsupported(
      @Nullable String extensionType, @Nullable String extensionKey, String path) {
    paths.add(path);
    return unsupportedMacros.add(AdfText.extensionLabel(extensionType, extensionKey));
  }

  List<Diagnostic> build() {
    if (unsupportedMacros.isEmpty()) {
      return List.of();
    }
    return List.of(
        new Diagnostic(
                Diagnostic.Code.UNSUPPORTED_MACRO,
                unsupportedMacros.size()
                    + " unsupported macro(s) rendered as placeholders; original content not"
                    + " represented: "
                    + String.join(", ", unsupportedMacros)
                    + ".")
            .at(paths));
  }
}
