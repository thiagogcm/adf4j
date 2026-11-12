package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfExtension;
import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.ast.UnknownMark;
import dev.nthings.adf4j.internal.Located;
import dev.nthings.adf4j.internal.analyze.AdfLossinessCollector;
import dev.nthings.adf4j.internal.resource.CallbackTracker;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.result.ConversionEffect;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/// What one render reports: callback failures, effects in document order, unresolved
/// dependencies, and content lossiness.
final class RenderEvents {
  private final CallbackTracker callbacks = new CallbackTracker();
  private final List<ConversionEffect> effects = new ArrayList<>();
  private final List<UnresolvedResource> unresolved = new ArrayList<>();
  private final List<String> unknownNodePaths = new ArrayList<>();
  private final List<String> unknownMarkPaths = new ArrayList<>();
  private final MacroDiagnostics macros = new MacroDiagnostics();
  private final ConversionOptions options;

  RenderEvents(ConversionOptions options) {
    this.options = options;
  }

  /// Records the marks of the node at `path` that the output loses. Called once per rendered node,
  /// before its content renders; nodes whose marks rendering ignores record nothing.
  void observeMarks(AdfNode node, String path) {
    if (!AdfLossinessCollector.rendersMarks(node)) {
      return;
    }
    for (var i = 0; i < node.marks().size(); i++) {
      var mark = node.marks().get(i);
      if (mark instanceof UnknownMark) {
        unknownMarkPaths.add(Located.mark(path, i));
        effect(
            ConversionEffect.Kind.CONTENT_DROPPED, Located.mark(path, i), "Unknown mark dropped");
      } else if (TextMarkRenderer.omitsVisual(mark, options.htmlVisualMarks())) {
        effect(
            ConversionEffect.Kind.STYLE_OMITTED,
            Located.mark(path, i),
            "Visual formatting omitted");
      }
    }
  }

  /// Records an unknown node the active policy rendered; the policy's effect is the caller's.
  void unknownNode(String path) {
    unknownNodePaths.add(path);
  }

  List<Diagnostic> diagnostics() {
    return Stream.of(
            macros.build(),
            AdfLossinessCollector.diagnostics(
                options.unknownNodePolicy(), unknownNodePaths, unknownMarkPaths),
            callbacks.diagnostics())
        .flatMap(List::stream)
        .toList();
  }

  List<ConversionEffect> effects() {
    return List.copyOf(effects);
  }

  List<UnresolvedResource> unresolved() {
    return List.copyOf(unresolved);
  }

  CallbackTracker callbacks() {
    return callbacks;
  }

  /// Records an extension rendered as a placeholder; true if its label is new to this render.
  boolean unsupportedExtension(AdfExtension node, String path) {
    effect(
        ConversionEffect.Kind.PLACEHOLDER, path, "Unsupported extension: " + node.extensionKey());
    return macros.recordUnsupported(node.extensionType(), node.extensionKey(), path);
  }

  void effect(ConversionEffect.Kind kind, String path, String message) {
    effects.add(new ConversionEffect(kind, path, message));
  }

  /// Runs `lookup`; a `null` answer records the dependency as unresolved, with the reason the
  /// lookup's callbacks imply, and returns `null` so the caller renders `fallback`.
  <T> @Nullable T resolve(
      Supplier<@Nullable T> lookup,
      UnresolvedResource.Kind kind,
      @Nullable String id,
      String path,
      String fallback,
      @Nullable String placeholder) {
    var failuresBefore = callbacks.failureCount();
    var resolved = lookup.get();
    if (resolved == null) {
      unresolved(kind, id, path, callbacks.reasonSince(failuresBefore), fallback, placeholder);
    }
    return resolved;
  }

  /// Records a dependency missing after every fallback; a non-null `placeholder` adds an effect.
  void unresolved(
      UnresolvedResource.Kind kind,
      @Nullable String id,
      String path,
      UnresolvedResource.Reason reason,
      String fallback,
      @Nullable String placeholder) {
    unresolved.add(new UnresolvedResource(kind, id == null ? "" : id, path, reason, fallback));
    if (placeholder != null) {
      effect(ConversionEffect.Kind.PLACEHOLDER, path, placeholder);
    }
  }
}
