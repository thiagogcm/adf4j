package dev.nthings.adf4j.internal.engine;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.analyze.AdfDocumentAnalyzer;
import dev.nthings.adf4j.internal.render.AdfDocumentRenderer;
import dev.nthings.adf4j.internal.render.RenderEnvironment;
import dev.nthings.adf4j.internal.render.RenderEvents;
import dev.nthings.adf4j.internal.resource.ResourceResolver;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.DocumentContext;
import dev.nthings.adf4j.options.ResourceResolvers;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.AdfException;
import dev.nthings.adf4j.result.AnalysisResult;
import dev.nthings.adf4j.result.ConversionEffect;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.ParseResult;
import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.ArrayList;
import java.util.List;

/// Renderer-neutral `parse → analyze → render` pipeline. Stateless and thread-safe, so one
/// instance serves the process across any number of renderer backends.
public final class AdfPipeline {

  /// Builds a backend's public result; failures get an empty body and no unresolved or effects.
  @FunctionalInterface
  public interface ResultFactory<R> {
    R create(
        String body,
        ContentMetadata metadata,
        List<Diagnostic> diagnostics,
        List<UnresolvedResource> unresolved,
        List<ConversionEffect> effects);
  }

  private final AdfDocumentAnalyzer analyzer;

  private AdfPipeline(AdfDocumentAnalyzer analyzer) {
    this.analyzer = analyzer;
  }

  public static AdfPipeline createDefault() {
    return new AdfPipeline(AdfDocumentAnalyzer.createDefault());
  }

  public AnalysisResult analyze(
      ParseResult parsed, DocumentContext context, UnknownNodePolicy unknownNodePolicy) {
    var document = parsed.document();
    if (document == null) {
      return AnalysisResult.failed(parsed.diagnostics());
    }
    var result = analyze(document, context, unknownNodePolicy);
    return new AnalysisResult(
        result.metadata(), mergeDiagnostics(parsed.diagnostics(), result.diagnostics()));
  }

  public AnalysisResult analyze(
      AdfDocument document, DocumentContext context, UnknownNodePolicy unknownNodePolicy) {
    var attachments = AttachmentInventory.from(context.attachments());
    var result = analyzer.analyze(document, unknownNodePolicy, attachments);
    return new AnalysisResult(result.facts().metadata(), result.diagnostics());
  }

  public <R> R render(
      ParseResult parsed,
      DocumentContext context,
      UnknownNodePolicy unknownNodePolicy,
      ResourceResolvers resolvers,
      AdfDocumentRenderer renderer,
      ResultFactory<R> factory) {
    var diagnostics = parsed.diagnostics();
    var document = parsed.document();
    // Failed parsing yields a failed result without invoking the renderer, even with a title set.
    if (document == null) {
      return failed(factory, ContentMetadata.empty(), diagnostics);
    }
    var attachments = AttachmentInventory.from(context.attachments());
    var facts = analyzer.facts(document, unknownNodePolicy, attachments);
    var events = new RenderEvents();
    var resources = new ResourceResolver(resolvers, attachments, events.callbacks());
    var environment =
        new RenderEnvironment(
            facts.outline(), context, unknownNodePolicy, attachments, resources, events);
    try {
      var body = renderer.render(document, environment);
      // Only visited content contributes render diagnostics.
      var merged = mergeDiagnostics(diagnostics, events.diagnostics(unknownNodePolicy));
      return factory.create(body, facts.metadata(), merged, events.unresolved(), events.effects());
    } catch (AdfException failure) {
      return failed(
          factory, facts.metadata(), mergeDiagnostics(diagnostics, failure.diagnostics()));
    }
  }

  private static <R> R failed(
      ResultFactory<R> factory, ContentMetadata metadata, List<Diagnostic> diagnostics) {
    return factory.create("", metadata, diagnostics, List.of(), List.of());
  }

  private static List<Diagnostic> mergeDiagnostics(
      List<Diagnostic> earlier, List<Diagnostic> later) {
    if (later.isEmpty()) {
      return earlier;
    }
    var merged = new ArrayList<Diagnostic>(earlier.size() + later.size());
    merged.addAll(earlier);
    merged.addAll(later);
    return merged;
  }
}
