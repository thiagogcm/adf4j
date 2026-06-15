package dev.nthings.adf4j.internal.render;

final class MarkdownInputCleaner {

  private MarkdownInputCleaner() {}

  static String clean(String markdown) {
    if (markdown.isEmpty()) {
      return markdown;
    }

    var builder = new StringBuilder(markdown.length());
    for (var index = 0; index < markdown.length(); index++) {
      var current = markdown.charAt(index);
      switch (current) {
        case '\u00A0':
        case '\u202F':
        case '\u2003':
          builder.append(' ');
          break;
        case '\u2028':
          builder.append('\n');
          break;
        case '\u200B':
        case '\u2060':
        case '\u009D':
        case '\u00AD':
          break;
        default:
          builder.append(current);
          break;
      }
    }
    return builder.toString();
  }
}
