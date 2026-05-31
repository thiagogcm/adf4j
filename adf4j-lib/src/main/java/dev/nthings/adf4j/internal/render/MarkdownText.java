package dev.nthings.adf4j.internal.render;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class MarkdownText {

  private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\\R");

  private MarkdownText() {
  }

  public static int clampHeadingLevel(int level) {
    return Math.clamp(level, 1, 6);
  }

  public static String dateFromTimestamp(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return "";
    }

    try {
      var value = Long.parseLong(timestamp);
      var date = Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate();
      return date.toString();
    } catch (NumberFormatException _) {
      return timestamp;
    }
  }

  public static List<String> splitLines(String value) {
    if (value == null) {
      return List.of();
    }
    return Arrays.asList(LINE_BREAK_PATTERN.split(value, -1));
  }

  public static String escapeLinkText(String value) {
    return Objects.requireNonNullElse(value, "").replace("[", "\\[").replace("]", "\\]");
  }
}
