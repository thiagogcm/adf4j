package dev.nthings.adf4j.options;

import java.util.Objects;

/// Serializable Markdown rendering policy. Headerless tables keep every original data row.
public record MarkdownOptions(
    boolean imageSizeAttributes,
    TableFallback tableFallback,
    boolean htmlVisualMarks,
    boolean collapseHardBreaks,
    boolean escapeParentheses) {
  public MarkdownOptions {
    Objects.requireNonNull(tableFallback, "tableFallback");
  }

  public static MarkdownOptions defaults() {
    return new MarkdownOptions(false, TableFallback.GFM_EMPTY_HEADER, false, false, false);
  }

  public MarkdownOptions withImageSizeAttributes(boolean value) {
    return new MarkdownOptions(
        value, tableFallback, htmlVisualMarks, collapseHardBreaks, escapeParentheses);
  }

  public MarkdownOptions withTableFallback(TableFallback value) {
    return new MarkdownOptions(
        imageSizeAttributes, value, htmlVisualMarks, collapseHardBreaks, escapeParentheses);
  }

  public MarkdownOptions withHtmlVisualMarks(boolean value) {
    return new MarkdownOptions(
        imageSizeAttributes, tableFallback, value, collapseHardBreaks, escapeParentheses);
  }

  public MarkdownOptions withCollapseHardBreaks(boolean value) {
    return new MarkdownOptions(
        imageSizeAttributes, tableFallback, htmlVisualMarks, value, escapeParentheses);
  }

  public MarkdownOptions withEscapeParentheses(boolean value) {
    return new MarkdownOptions(
        imageSizeAttributes, tableFallback, htmlVisualMarks, collapseHardBreaks, value);
  }
}
