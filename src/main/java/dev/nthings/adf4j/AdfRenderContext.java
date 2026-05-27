package dev.nthings.adf4j;

public interface AdfRenderContext {

  static AdfRenderContext none() {
    return NoAdfRenderContext.INSTANCE;
  }
}

enum NoAdfRenderContext implements AdfRenderContext {
  INSTANCE
}
