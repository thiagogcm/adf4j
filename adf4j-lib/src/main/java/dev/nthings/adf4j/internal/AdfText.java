package dev.nthings.adf4j.internal;

import java.time.Instant;
import java.time.ZoneOffset;

/** Phase-neutral helpers interpreting raw ADF scalar values, shared by the analyze and render phases. */
public final class AdfText {

  private AdfText() {
  }

  public static int clampHeadingLevel(int level) {
    return Math.clamp(level, 1, 6);
  }

  // Values under 10^11 are read as epoch seconds, larger ones as epoch milliseconds; a blank or
  // non-numeric timestamp is returned as-is.
  public static String dateFromTimestamp(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return "";
    }

    try {
      var value = Long.parseLong(timestamp);
      var instant = Math.abs(value) < 100_000_000_000L
          ? Instant.ofEpochSecond(value)
          : Instant.ofEpochMilli(value);
      var date = instant.atZone(ZoneOffset.UTC).toLocalDate();
      return date.toString();
    } catch (NumberFormatException _) {
      return timestamp;
    }
  }
}
