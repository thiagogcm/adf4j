package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.ast.AdfUnknown;
import dev.nthings.adf4j.ast.LayoutSection;
import dev.nthings.adf4j.ast.UnknownMark;
import dev.nthings.adf4j.internal.Located;
import dev.nthings.adf4j.internal.analyze.AdfLossinessCollector;
import dev.nthings.adf4j.internal.resource.CallbackTracker;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.result.ConversionEffect;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/// What one render reports: callback failures, effects in document order, unresolved
/// dependencies, and content lossiness.
final class RenderEvents {
  private final CallbackTracker callbacks = new CallbackTracker();
  private final List<ConversionEffect> effects = new ArrayList<>();
  private final List<UnresolvedResource> unresolved = new ArrayList<>();
  private final AdfLossinessCollector unknowns = new AdfLossinessCollector();
  private final Set<String> observed = new HashSet<>();
  private final ConversionOptions options;

  RenderEvents(ConversionOptions options) {
    this.options = options;
  }

  /// Records what the node at `path` changes; each source position counts once.
  void observe(AdfNode node, String path) {
    if (!observed.add(path)) {
      return;
    }
    if (node instanceof AdfBlock block) {
      unknowns.visitBlock(block, path);
    } else if (node instanceof AdfInline inline) {
      unknowns.visitInline(inline, path);
    }
    if (node instanceof AdfUnknown) {
      switch (options.unknownNodePolicy()) {
        case SKIP -> effect(ConversionEffect.Kind.CONTENT_DROPPED, path, "Unknown node dropped");
        case PLACEHOLDER ->
            effect(
                ConversionEffect.Kind.PLACEHOLDER,
                path,
                "Unknown node replaced with a placeholder");
        case PRESERVE_RAW, FAIL -> {}
      }
    }
    if (node instanceof LayoutSection) {
      effect(
          ConversionEffect.Kind.STRUCTURE_CHANGED,
          path,
          "Columns flattened into sequential blocks");
    }
    for (var i = 0; i < node.marks().size(); i++) {
      var mark = node.marks().get(i);
      if (mark instanceof UnknownMark) {
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

  List<Diagnostic> diagnostics() {
    return Stream.concat(
            unknowns.build(options.unknownNodePolicy()).stream(), callbacks.diagnostics().stream())
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

  void effect(ConversionEffect.Kind kind, String path, String message) {
    effects.add(new ConversionEffect(kind, path, message));
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
