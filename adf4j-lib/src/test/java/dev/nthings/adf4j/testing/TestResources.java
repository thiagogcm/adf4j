package dev.nthings.adf4j.testing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class TestResources {

  private TestResources() {}

  public static String read(String path) throws IOException {
    try (var stream = TestResources.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IOException("Missing test resource: " + path);
      }

      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  public static Path root(String path) {
    var resource = TestResources.class.getClassLoader().getResource(path);
    if (resource == null) {
      throw new IllegalStateException("Missing test resource directory: " + path);
    }

    try {
      return Path.of(resource.toURI());
    } catch (URISyntaxException exception) {
      throw new IllegalStateException("Invalid test resource URI: " + path, exception);
    }
  }

  public static String stripFinalNewline(String value) {
    if (value.endsWith("\r\n")) {
      return value.substring(0, value.length() - 2);
    }
    if (value.endsWith("\n") || value.endsWith("\r")) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }
}
