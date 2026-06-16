package dev.nthings.adf4j.wasm;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.result.MarkdownResult;
import java.io.IOException;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSString;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Web Image entry point that exposes adf4j on {@code globalThis}. */
public final class WasmBridge {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String UNKNOWN_VERSION = "unknown";
  private static final String VERSION = loadVersion();
  private static final String VERSION_RESOURCE = "/dev/nthings/adf4j/wasm/adf4j-wasm.properties";
  private static final String VERSION_KEY = "version";

  public static void main(String[] args) {
    installApi(
        (var json) -> JSString.of(convert(json.asString())),
        (var json) -> JSString.of(convertJson(json.asString())),
        () -> JSString.of(VERSION));
  }

  private static String convert(String adfJson) {
    try {
      return AdfToMarkdown.create().convert(adfJson).body();
    } catch (RuntimeException failure) {
      return "ERROR: " + failure;
    }
  }

  private static String convertJson(String adfJson) {
    try {
      return writeJson(ConversionEnvelope.success(AdfToMarkdown.create().convert(adfJson)));
    } catch (RuntimeException failure) {
      return writeJson(ConversionEnvelope.failure(String.valueOf(failure)));
    }
  }

  private static String loadVersion() {
    var stream = WasmBridge.class.getResourceAsStream(VERSION_RESOURCE);
    if (stream == null) {
      return UNKNOWN_VERSION;
    }
    try (stream) {
      var properties = new Properties();
      properties.load(stream);
      return properties.getProperty(VERSION_KEY, UNKNOWN_VERSION);
    } catch (IOException failure) {
      return UNKNOWN_VERSION;
    }
  }

  private static String writeJson(ConversionEnvelope envelope) {
    return JSON.writeValueAsString(envelope.toJsonNode(JSON.createObjectNode()));
  }

  private record ConversionEnvelope(
      boolean ok, boolean lossy, int warnings, int errors, String body, String error) {

    private static ConversionEnvelope success(MarkdownResult result) {
      var warnings = 0;
      var errors = 0;
      for (var diagnostic : result.diagnostics()) {
        switch (diagnostic.severity()) {
          case WARNING -> warnings++;
          case ERROR -> errors++;
          default -> {}
        }
      }
      return new ConversionEnvelope(true, result.wasLossy(), warnings, errors, result.body(), "");
    }

    private static ConversionEnvelope failure(String error) {
      return new ConversionEnvelope(false, false, 0, 0, "", error);
    }

    private ObjectNode toJsonNode(ObjectNode node) {
      node.put("ok", ok);
      if (ok) {
        node.put("lossy", lossy);
        node.put("warnings", warnings);
        node.put("errors", errors);
        node.put("body", body);
      } else {
        node.put("error", error);
      }
      return node;
    }
  }

  @JS(
      args = {"convertFn", "convertJsonFn", "versionFn"},
      value =
          """
      const str = (value) => typeof value === 'string' ? value : value.asString();
      const api = (globalThis.adf4j ??= {});
      Object.assign(api, {
        convert: (json) => str(convertFn(json)),
        convertJson: (json) => JSON.parse(str(convertJsonFn(json))),
        version: () => str(versionFn()),
        ready: true
      });
      if (typeof globalThis.__adf4jOnReady === 'function') {
        globalThis.__adf4jOnReady();
      }
      """)
  private static native void installApi(
      Function<JSString, JSString> convertFn,
      Function<JSString, JSString> convertJsonFn,
      Supplier<JSString> versionFn);

  private WasmBridge() {}
}
