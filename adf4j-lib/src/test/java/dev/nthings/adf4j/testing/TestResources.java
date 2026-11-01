package dev.nthings.adf4j.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestResources {

  private static final String SPEC_ROOT = "adf/spec";

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

  /// The `.json` fixtures under `adf/spec`, sorted, as resource paths for {@link #read(String)}.
  public static List<String> specFixtures() {
    var root = root(SPEC_ROOT);
    try (var files = Files.walk(root)) {
      return files
          .filter(path -> path.toString().endsWith(".json"))
          .map(path -> SPEC_ROOT + "/" + root.relativize(path).toString().replace('\\', '/'))
          .sorted()
          .toList();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  /// The off-spec corpus: each case's name and its ADF JSON, in file order.
  public static Map<String, String> corpus() {
    var cases = new LinkedHashMap<String, String>();
    try {
      read("adf/characterization/corpus.tsv")
          .lines()
          .forEach(
              line -> {
                var tab = line.indexOf('\t');
                cases.put(line.substring(0, tab), line.substring(tab + 1));
              });
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    return cases;
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
