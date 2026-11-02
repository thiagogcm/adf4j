package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Collects the extensions a render emitted as bare `[Extension: …]` placeholders: unsupported
/// Confluence macros (e.g. `detailssummary`) whose content is lost. Fresh per render and driven by
/// a single-threaded traversal, so the {@link LinkedHashSet} needs no synchronization.
final class MacroDiagnostics {

  private final Set<String> unsupportedMacros = new LinkedHashSet<>();

  /// Records one unsupported macro, returning whether this render had not seen its label yet.
  boolean recordUnsupported(@Nullable String extensionType, @Nullable String extensionKey) {
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
                + " unsupported macro(s) rendered as placeholders; original content not represented: "
                + String.join(", ", unsupportedMacros)
                + "."));
  }
}
