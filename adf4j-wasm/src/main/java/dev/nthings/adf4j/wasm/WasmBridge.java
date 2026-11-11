package dev.nthings.adf4j.wasm;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.result.AnalysisResult;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSString;
import tools.jackson.core.JacksonException;

/// Installs convert, analyze, and version on globalThis.adf4j and signals readiness.
/// Configuration contains portable rendering policy and document context.
/// Input failures use the same result projection as the library and CLI.
public final class WasmBridge {

  private static final String UNKNOWN_VERSION = "unknown";
  private static final String VERSION_RESOURCE = "/dev/nthings/adf4j/wasm/adf4j-wasm.properties";
  private static final String VERSION_KEY = "version";
  private static final String VERSION = loadVersion();

  public static void main(String[] args) {
    installApi(
        (json, config) -> JSString.of(run(json.asString(), config.asString(), false)),
        (json, config) -> JSString.of(run(json.asString(), config.asString(), true)),
        () -> JSString.of(VERSION));
  }

  private static String run(String json, String config, boolean analyze) {
    ConversionOptions options;
    try {
      options =
          config.isBlank() ? ConversionOptions.defaults() : ConversionOptions.fromJson(config);
    } catch (IllegalArgumentException | JacksonException invalid) {
      var failure =
          new MarkdownResult(
              "",
              ContentMetadata.empty(),
              List.of(
                  new Diagnostic(
                      Diagnostic.Code.INVALID_OPTIONS,
                      "Invalid configuration: " + invalid.getMessage())),
              List.of(),
              List.of());
      return AdfJson.writeResult(
          analyze ? new AnalysisResult(ContentMetadata.empty(), failure.diagnostics()) : failure);
    }
    var converter = AdfToMarkdown.with(options);
    return AdfJson.writeResult(
        analyze ? converter.analyze(AdfJson.parse(json)) : converter.convert(json));
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

  @JS(
      args = {"convertFn", "analyzeFn", "versionFn"},
      value =
          """
      const str = (value) => typeof value === 'string' ? value : value.asString();
      const ctx = (value) =>
        value == null ? '' : typeof value === 'string' ? value : String(JSON.stringify(value));
      const api = (globalThis.adf4j ??= {});
      Object.assign(api, {
        convert: (json, config) => JSON.parse(str(convertFn(String(json ?? ''), ctx(config)))),
        analyze: (json, config) => JSON.parse(str(analyzeFn(String(json ?? ''), ctx(config)))),
        version: () => str(versionFn()),
        ready: true
      });
      if (typeof globalThis.__adf4jOnReady === 'function') {
        globalThis.__adf4jOnReady();
      }
      """)
  private static native void installApi(
      BiFunction<JSString, JSString, JSString> convertFn,
      BiFunction<JSString, JSString, JSString> analyzeFn,
      Supplier<JSString> versionFn);

  private WasmBridge() {}
}
