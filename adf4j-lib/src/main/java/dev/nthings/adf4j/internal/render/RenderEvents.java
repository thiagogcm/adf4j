package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.internal.SourceIndex;
import dev.nthings.adf4j.internal.analyze.AdfLossinessCollector;
import dev.nthings.adf4j.internal.analyze.ConversionEffects;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.ConversionEffect;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

final class RenderEvents {
  final List<Diagnostic> diagnostics = new ArrayList<>();
  final List<ConversionEffect> effects = new ArrayList<>();
  final SourceIndex source;
  final ConversionEffects changes;
  final AdfLossinessCollector unknowns;
  final Set<AdfNode> observed = Collections.newSetFromMap(new IdentityHashMap<>());

  void observe(AdfNode node) {
    if (observed.add(node)) {
      changes.visit(node);
      if (node instanceof AdfBlock block) unknowns.visitBlock(block);
      else if (node instanceof AdfInline inline) unknowns.visitInline(inline);
    }
  }

  List<Diagnostic> allDiagnostics(UnknownNodePolicy policy) {
    return Stream.concat(unknowns.build(policy).stream(), diagnostics.stream()).toList();
  }

  List<ConversionEffect> allEffects() {
    return Stream.concat(changes.effects().stream(), effects.stream()).toList();
  }

  RenderEvents(AdfDocument document, ConversionOptions options) {
    source = new SourceIndex(document);
    changes = new ConversionEffects(document, options);
    unknowns = new AdfLossinessCollector(document);
  }

  <T> @Nullable T call(String hook, String path, Supplier<@Nullable T> callback) {
    try {
      return callback.get();
    } catch (RuntimeException failure) {
      diagnostics.add(
          new Diagnostic(
              Diagnostic.Code.CALLBACK_FAILED,
              hook + " failed: " + failure.getMessage(),
              failure,
              List.of(path),
              Map.of("callback", hook)));
      return null;
    }
  }

  int callbackFailures() {
    return diagnostics.size();
  }

  UnresolvedResource.Reason reasonSince(int failures) {
    return callbackFailures() > failures
        ? UnresolvedResource.Reason.CALLBACK_FAILED
        : UnresolvedResource.Reason.NO_ANSWER;
  }

  void effect(ConversionEffect.Kind kind, String path, String message) {
    effects.add(new ConversionEffect(kind, path, message));
  }
}
