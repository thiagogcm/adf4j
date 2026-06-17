package dev.nthings.adf4j.ast;

/// A data-consumer (`dataConsumer`) mark linking a node to a data source it derives from.
/// Non-visual metadata with no formatting; it is dropped from the output, leaving the content
/// intact.
public record DataConsumer() implements AdfMark {

  @Override
  public String type() {
    return "dataConsumer";
  }
}
