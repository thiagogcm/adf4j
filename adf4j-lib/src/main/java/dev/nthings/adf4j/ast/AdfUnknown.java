package dev.nthings.adf4j.ast;

/// A node or mark whose `type` the parser does not recognize: {@link UnknownBlock},
/// {@link UnknownInline} or {@link UnknownMark}. It keeps its original JSON, so it survives parsing
/// and writing without loss.
public sealed interface AdfUnknown permits UnknownBlock, UnknownInline, UnknownMark {

  String type();

  String rawJson();
}
