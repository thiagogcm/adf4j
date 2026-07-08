package dev.nthings.adf4j.wasm;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.MarkdownResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSString;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// Web Image entry point. `main` installs `globalThis.adf4j` with `convert(json, context?)`,
/// `convertJson(json, context?)`, `version()`, and a `ready` flag, then fires
/// `globalThis.__adf4jOnReady()` if present. That callback is the only handshake a Node/browser
/// host has, since the wasm image has no stdin or host filesystem. `context` is data, not
/// callbacks (functions cannot cross the wasm boundary): an object or JSON string of the shape
/// `{attachments: [{fileId, title?, mediaType?, downloadUrl?}]}`, feeding the Confluence render
/// context. Conversion never throws across the boundary: failures come back in-band, as an
/// `ERROR: …` string from `convert` or `{ok:false, error}` from `convertJson`.
public final class WasmBridge {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final AdfToMarkdown CONVERTER = AdfToMarkdown.create();
  private static final MarkdownOptions DEFAULT_OPTIONS = MarkdownOptions.defaults();
  private static final String UNKNOWN_VERSION = "unknown";
  private static final String VERSION = loadVersion();
  private static final String VERSION_RESOURCE = "/dev/nthings/adf4j/wasm/adf4j-wasm.properties";
  private static final String VERSION_KEY = "version";

  public static void main(String[] args) {
    installApi(
        (var json, var context) -> JSString.of(convert(json.asString(), context.asString())),
        (var json, var context) -> JSString.of(convertJson(json.asString(), context.asString())),
        () -> JSString.of(VERSION));
  }

  private static String convert(String adfJson, String contextJson) {
    try {
      return CONVERTER.convert(adfJson, optionsFor(contextJson)).body();
    } catch (RuntimeException failure) {
      return "ERROR: " + failure;
    }
  }

  private static String convertJson(String adfJson, String contextJson) {
    try {
      return writeJson(
          ConversionEnvelope.success(CONVERTER.convert(adfJson, optionsFor(contextJson))));
    } catch (RuntimeException failure) {
      return writeJson(ConversionEnvelope.failure(String.valueOf(failure)));
    }
  }

  // A blank payload keeps the defaults; an unparsable one throws, surfacing in-band.
  private static MarkdownOptions optionsFor(String contextJson) {
    if (contextJson == null || contextJson.isBlank()) {
      return DEFAULT_OPTIONS;
    }
    var root = JSON.readTree(contextJson);
    var attachments = root.path("attachments");
    if (!attachments.isArray()) {
      return DEFAULT_OPTIONS;
    }
    var references = new ArrayList<AttachmentReference>();
    for (var item : attachments) {
      var fileId = string(item, "fileId");
      if (fileId == null || fileId.isBlank()) {
        continue;
      }
      references.add(
          new AttachmentReference(
              fileId,
              string(item, "title"),
              string(item, "mediaType"),
              string(item, "downloadUrl")));
    }
    // A present (even empty) attachments array is an authoritative inventory.
    return DEFAULT_OPTIONS.withConfluenceContext(
        ConfluenceRenderContext.empty().withAttachmentReferences(references));
  }

  private static @Nullable String string(JsonNode object, String field) {
    var value = object.get(field);
    return value == null || value.isNull() ? null : value.asString();
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
      const ctx = (value) =>
        value == null ? '' : typeof value === 'string' ? value : JSON.stringify(value);
      const api = (globalThis.adf4j ??= {});
      Object.assign(api, {
        convert: (json, context) => str(convertFn(json, ctx(context))),
        convertJson: (json, context) => JSON.parse(str(convertJsonFn(json, ctx(context)))),
        version: () => str(versionFn()),
        ready: true
      });
      if (typeof globalThis.__adf4jOnReady === 'function') {
        globalThis.__adf4jOnReady();
      }
      """)
  private static native void installApi(
      BiFunction<JSString, JSString, JSString> convertFn,
      BiFunction<JSString, JSString, JSString> convertJsonFn,
      Supplier<JSString> versionFn);

  private WasmBridge() {}
}
