package dev.nthings.adf4j;

/** Zero-configuration facade over a shared {@link AdfConverter} for converting ADF JSON to Markdown. */
public final class Adf {

  private static final AdfConverter SHARED = new AdfConverter();

  private Adf() {}

  public static String toMarkdown(String adfJson) {
    return SHARED.toMarkdown(adfJson, RenderOptions.defaults());
  }
}
