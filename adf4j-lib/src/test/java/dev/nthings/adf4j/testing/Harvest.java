package dev.nthings.adf4j.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import tools.jackson.databind.json.JsonMapper;

// Temporary: records actual values while loose assertions are tightened.
public final class Harvest {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  public static void record(String key, Object actual) {
    try {
      Files.writeString(
          Path.of(System.getProperty("java.io.tmpdir"), "adf4j-harvest.tsv"),
          key + "\t" + JSON.writeValueAsString(String.valueOf(actual)) + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
