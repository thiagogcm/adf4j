package dev.nthings.adf4j.internal;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;

/// Phase-neutral helpers interpreting raw ADF scalar values, shared by the analyze and render
/// phases.
public final class AdfText {

  private AdfText() {}

  public static int clampHeadingLevel(int level) {
    return Math.clamp(level, 1, 6);
  }

  // Values under 10^11 are read as epoch seconds, larger ones as epoch milliseconds; a blank or
  // non-numeric timestamp is returned as-is.
  public static String dateFromTimestamp(@Nullable String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return "";
    }

    try {
      var value = Long.parseLong(timestamp);
      // Guard Long.MIN_VALUE before the split: Math.abs would stay negative and steer it wrong.
      var instant =
          value != Long.MIN_VALUE && Math.abs(value) < 100_000_000_000L
              ? Instant.ofEpochSecond(value)
              : Instant.ofEpochMilli(value);
      return instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
    } catch (NumberFormatException | DateTimeException _) {
      return timestamp;
    }
  }
}
