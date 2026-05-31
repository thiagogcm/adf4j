package dev.nthings.adf4j;

/** Zero-configuration facade over a shared {@link AdfToMarkdown} for converting ADF JSON to Markdown. */
public final class Adf {

  private static final AdfToMarkdown SHARED = AdfToMarkdown.create();

  private Adf() {}

  public static String toMarkdown(String adfJson) {
    return SHARED.toMarkdown(adfJson);
  }
}
