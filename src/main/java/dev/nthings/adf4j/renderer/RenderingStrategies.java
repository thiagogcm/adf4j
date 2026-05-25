package dev.nthings.adf4j.renderer;

public final class RenderingStrategies {

  private static final RenderingStrategy STORAGE = new StorageRenderingStrategy();
  private static final RenderingStrategy PRESENTATION = new PresentationRenderingStrategy();

  private RenderingStrategies() {
  }

  public static RenderingStrategy storage() {
    return STORAGE;
  }

  public static RenderingStrategy presentation() {
    return PRESENTATION;
  }
}
