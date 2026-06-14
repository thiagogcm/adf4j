package dev.nthings.adf4j.wasm;

import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSString;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;

/**
 * GraalVM Web Image entry point exposing the adf4j converter to JavaScript. The wasm backend has no
 * stdin and only an in-memory filesystem, so {@code main} publishes plain {@code (string) -> string}
 * functions onto {@code globalThis} via the {@code @JS} helper pattern (the stand-in while
 * {@code @JS.Export} is unimplemented), then calls {@code globalThis.__adf4jOnReady()}. A JS host
 * installs that callback before loading the image, then calls the functions in-process. See
 * {@code src/test/js} for a consumer.
 */
public final class WasmBridge {

  private static final String VERSION = "1.0.0-SNAPSHOT";

  public static void main(String[] args) {
    registerConvert(json -> JSString.of(convert(json.asString())));
    registerConvertJson(json -> JSString.of(convertJson(json.asString())));
    registerVersion(() -> JSString.of(VERSION));
    signalReady();
  }

  /** Plain conversion: ADF JSON in, Markdown body out. */
  private static String convert(String adfJson) {
    try {
      return AdfToMarkdown.create().convert(adfJson).body();
    } catch (RuntimeException failure) {
      return "ERROR: " + failure;
    }
  }

  /** Richer envelope so the JS side can see lossiness and diagnostics, marshalled as JSON. */
  private static String convertJson(String adfJson) {
    try {
      MarkdownResult result = AdfToMarkdown.create().convert(adfJson);
      int warnings = 0;
      int errors = 0;
      for (Diagnostic diagnostic : result.diagnostics()) {
        if (diagnostic.severity() == Diagnostic.Severity.WARNING) {
          warnings++;
        } else if (diagnostic.severity() == Diagnostic.Severity.ERROR) {
          errors++;
        }
      }
      return "{\"ok\":true,\"lossy\":" + result.wasLossy()
          + ",\"warnings\":" + warnings
          + ",\"errors\":" + errors
          + ",\"body\":" + jsonString(result.body()) + "}";
    } catch (RuntimeException failure) {
      return "{\"ok\":false,\"error\":" + jsonString(String.valueOf(failure)) + "}";
    }
  }

  private static String jsonString(String value) {
    StringBuilder sb = new StringBuilder(value.length() + 2);
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.append('"').toString();
  }

  // The wasm boundary may hand a returned JSString back as a primitive JS string or as a JSValue
  // proxy, so coerce defensively in both directions.
  @JS(args = {"fn"}, value =
      "const str = (v) => typeof v === 'string' ? v : v.asString();"
      + "(globalThis.adf4j ??= {}).convert = (s) => str(fn(s));")
  private static native void registerConvert(Function<JSString, JSString> fn);

  @JS(args = {"fn"}, value =
      "const str = (v) => typeof v === 'string' ? v : v.asString();"
      + "(globalThis.adf4j ??= {}).convertJson = (s) => JSON.parse(str(fn(s)));")
  private static native void registerConvertJson(Function<JSString, JSString> fn);

  @JS(args = {"fn"}, value =
      "const str = (v) => typeof v === 'string' ? v : v.asString();"
      + "(globalThis.adf4j ??= {}).version = () => str(fn());")
  private static native void registerVersion(Supplier<JSString> fn);

  @JS(value = "globalThis.adf4j ??= {}; globalThis.adf4j.ready = true;"
      + " if (typeof globalThis.__adf4jOnReady === 'function') globalThis.__adf4jOnReady();")
  private static native void signalReady();

  private WasmBridge() {}
}
