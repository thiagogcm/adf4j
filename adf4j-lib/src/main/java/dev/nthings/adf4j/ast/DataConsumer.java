package dev.nthings.adf4j.ast;

public record DataConsumer() implements AdfMark {

  @Override
  public String type() {
    return "dataConsumer";
  }
}
