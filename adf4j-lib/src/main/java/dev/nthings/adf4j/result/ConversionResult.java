package dev.nthings.adf4j.result;

import dev.nthings.adf4j.metadata.ContentMetadata;
import java.util.List;

/// Outcome of converting an ADF document into a rendered representation (such as Markdown or HTML).
/// Non-sealed so separate renderer modules can implement typed conversion results while sharing
/// JSON serialization and quality-gate semantics.
public non-sealed interface ConversionResult extends OperationResult {

  String body();

  ContentMetadata metadata();

  List<UnresolvedResource> unresolved();

  List<ConversionEffect> effects();

  default String requireBody() {
    requireSuccess();
    return body();
  }
}
