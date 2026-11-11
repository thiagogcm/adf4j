package dev.nthings.adf4j.ast;

/// A data-consumer (`dataConsumer`) mark linking a node to a data source it derives from. It is
/// non-visual and dropped from the output, leaving the content intact.
public record DataConsumer(Attributes attrs) implements MediaMark {

  @Override
  public String type() {
    return "dataConsumer";
  }
}
