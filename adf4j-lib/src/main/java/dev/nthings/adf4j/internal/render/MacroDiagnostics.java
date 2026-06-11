package dev.nthings.adf4j.internal.render;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.Diagnostic.Severity;

/**
 * Collects the extensions a render emitted as bare {@code [Extension: …]} placeholders — unsupported
 * Confluence macros (e.g. {@code detailssummary}) whose content is lost. Fresh per render and driven by a
 * single-threaded traversal, so the {@link LinkedHashSet} needs no synchronization.
 */
final class MacroDiagnostics {

  private final Set<String> unsupportedMacros = new LinkedHashSet<>();

  void recordUnsupported(String extensionType, String extensionKey) {
    unsupportedMacros.add(label(extensionType, extensionKey));
  }

  private static String label(String extensionType, String extensionKey) {
    if (extensionType != null && extensionKey != null) {
      return extensionType + "/" + extensionKey;
    }
    if (extensionKey != null) {
      return extensionKey;
    }
    return "Extension";
  }

  List<Diagnostic> build() {
    if (unsupportedMacros.isEmpty()) {
      return List.of();
    }
    return List.of(new Diagnostic(
        "UNSUPPORTED_MACRO",
        unsupportedMacros.size()
            + " unsupported macro(s) rendered as placeholders; original content not represented: "
            + String.join(", ", unsupportedMacros) + ".",
        null,
        Severity.WARNING));
  }
}
