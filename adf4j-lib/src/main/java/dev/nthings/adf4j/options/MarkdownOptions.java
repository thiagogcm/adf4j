package dev.nthings.adf4j.options;

import java.util.Objects;

/// Serializable rendering policy. Headerless tables keep every original data row.
public record MarkdownOptions(
    UnknownNodePolicy unknownNodePolicy,
    boolean imageSizeAttributes,
    TableFallback tableFallback,
    boolean htmlVisualMarks,
    boolean collapseHardBreaks,
    boolean escapeParentheses) {
  public MarkdownOptions {
    Objects.requireNonNull(unknownNodePolicy, "unknownNodePolicy");
    Objects.requireNonNull(tableFallback, "tableFallback");
  }

  public static MarkdownOptions defaults() {
    return new MarkdownOptions(
        UnknownNodePolicy.PLACEHOLDER, false, TableFallback.GFM_EMPTY_HEADER, false, false, false);
  }

  public MarkdownOptions withUnknownNodePolicy(UnknownNodePolicy value) {
    return new MarkdownOptions(
        value,
        imageSizeAttributes,
        tableFallback,
        htmlVisualMarks,
        collapseHardBreaks,
        escapeParentheses);
  }

  public MarkdownOptions withImageSizeAttributes(boolean value) {
    return new MarkdownOptions(
        unknownNodePolicy,
        value,
        tableFallback,
        htmlVisualMarks,
        collapseHardBreaks,
        escapeParentheses);
  }

  public MarkdownOptions withTableFallback(TableFallback value) {
    return new MarkdownOptions(
        unknownNodePolicy,
        imageSizeAttributes,
        value,
        htmlVisualMarks,
        collapseHardBreaks,
        escapeParentheses);
  }

  public MarkdownOptions withHtmlVisualMarks(boolean value) {
    return new MarkdownOptions(
        unknownNodePolicy,
        imageSizeAttributes,
        tableFallback,
        value,
        collapseHardBreaks,
        escapeParentheses);
  }

  public MarkdownOptions withCollapseHardBreaks(boolean value) {
    return new MarkdownOptions(
        unknownNodePolicy,
        imageSizeAttributes,
        tableFallback,
        htmlVisualMarks,
        value,
        escapeParentheses);
  }

  public MarkdownOptions withEscapeParentheses(boolean value) {
    return new MarkdownOptions(
        unknownNodePolicy,
        imageSizeAttributes,
        tableFallback,
        htmlVisualMarks,
        collapseHardBreaks,
        value);
  }
}
