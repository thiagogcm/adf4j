package dev.nthings.adf4j.wasm;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.UnresolvedReferences;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSString;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// Web Image entry point. `main` installs `globalThis.adf4j` with `convert(json, context?)`,
/// `version()`, and a `ready` flag, then fires `globalThis.__adf4jOnReady()` if present. That
/// callback is the only handshake a Node/browser host has, since the wasm image has no stdin or
/// host filesystem.
///
/// `context` is data, not callbacks (functions cannot cross the wasm boundary): an object or JSON
/// string of the shape `{attachments?: [{fileId, title?, mediaType?, downloadUrl?}], title?}`,
/// feeding the attachment inventory and the document title. It is as strict as the CLI's
/// `--attachments` file: unknown keys and non-array attachments are errors, not "no inventory".
///
/// `convert` never throws across the boundary. It always answers
/// `{ok, body, wasLossy, diagnostics: [{code, severity, message}], error?}`, with the CLI's field
/// names, and `ok` is false, with `error` set, on invalid input or an invalid context.
public final class WasmBridge {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final MarkdownOptions DEFAULT_OPTIONS = MarkdownOptions.defaults();
  private static final Set<String> CONTEXT_KEYS = Set.of("attachments", "title");
  private static final Set<String> ATTACHMENT_KEYS =
      Set.of("fileId", "title", "mediaType", "downloadUrl");
  private static final MarkdownResult NO_RESULT =
      new MarkdownResult("", ContentMetadata.empty(), List.of(), UnresolvedReferences.empty());
  private static final String UNKNOWN_VERSION = "unknown";
  private static final String VERSION = loadVersion();
  private static final String VERSION_RESOURCE = "/dev/nthings/adf4j/wasm/adf4j-wasm.properties";
  private static final String VERSION_KEY = "version";

  public static void main(String[] args) {
    installApi(
        (var json, var context) -> JSString.of(convert(json.asString(), context.asString())),
        () -> JSString.of(VERSION));
  }

  private static String convert(String adfJson, String contextJson) {
    MarkdownOptions options;
    try {
      options = optionsFor(contextJson);
    } catch (IllegalArgumentException | JacksonException invalid) {
      return response(NO_RESULT, "invalid context: " + invalid.getMessage());
    }
    try {
      var parsed = AdfJson.parse(adfJson);
      var result = AdfToMarkdown.with(options).convert(parsed);
      return response(result, parsed.isValid() ? null : firstError(result));
    } catch (RuntimeException failure) {
      return response(NO_RESULT, String.valueOf(failure));
    }
  }

  // An invalid input is an ERROR diagnostic, except a blank one, which raises none.
  private static String firstError(MarkdownResult result) {
    return result.diagnostics().stream()
        .filter(diagnostic -> diagnostic.severity() == Diagnostic.Severity.ERROR)
        .map(Diagnostic::message)
        .findFirst()
        .orElse("ADF input is blank.");
  }

  // The result's fields one by one: a Diagnostic's cause must never reach the host.
  private static String response(MarkdownResult result, @Nullable String error) {
    var node = JSON.createObjectNode();
    node.put("ok", error == null);
    node.put("body", result.body());
    node.put("wasLossy", result.wasLossy());
    var diagnostics = node.putArray("diagnostics");
    for (var diagnostic : result.diagnostics()) {
      diagnostics
          .addObject()
          .put("code", diagnostic.code().name())
          .put("severity", diagnostic.severity().name())
          .put("message", diagnostic.message());
    }
    if (error != null) {
      node.put("error", error);
    }
    return JSON.writeValueAsString(node);
  }

  // A blank payload keeps the defaults.
  private static MarkdownOptions optionsFor(String contextJson) {
    if (contextJson.isBlank()) {
      return DEFAULT_OPTIONS;
    }
    var context = schemaObject(JSON.readTree(contextJson), CONTEXT_KEYS, "context");
    var options = DEFAULT_OPTIONS.withDocumentTitle(string(context, "title"));
    return context.has("attachments")
        ? options.withAttachments(attachments(context.get("attachments")))
        : options;
  }

  // A present (even empty) attachments array is an authoritative inventory.
  private static List<AttachmentReference> attachments(JsonNode attachments) {
    if (!attachments.isArray()) {
      throw new IllegalArgumentException("'attachments' must be an array");
    }
    var references = new ArrayList<AttachmentReference>();
    for (var item : attachments) {
      var entry = schemaObject(item, ATTACHMENT_KEYS, "attachments entry");
      var fileId = string(entry, "fileId");
      if (fileId == null || fileId.isBlank()) {
        throw new IllegalArgumentException("missing 'fileId' in attachments entry");
      }
      references.add(
          new AttachmentReference(
              fileId,
              string(entry, "title"),
              string(entry, "mediaType"),
              string(entry, "downloadUrl")));
    }
    return references;
  }

  // The node as an object with no keys outside `allowed`.
  private static ObjectNode schemaObject(JsonNode node, Set<String> allowed, String what) {
    if (!(node instanceof ObjectNode object)) {
      throw new IllegalArgumentException(what + " must be an object");
    }
    for (var key : object.propertyNames()) {
      if (!allowed.contains(key)) {
        throw new IllegalArgumentException("unknown key '" + key + "' in " + what);
      }
    }
    return object;
  }

  // A scalar field as a string, or null when absent or JSON null.
  private static @Nullable String string(JsonNode object, String field) {
    var value = object.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isContainer()) {
      throw new IllegalArgumentException("'" + field + "' must be a scalar value");
    }
    return value.asString();
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
      args = {"convertFn", "versionFn"},
      value =
          """
      const str = (value) => typeof value === 'string' ? value : value.asString();
      const ctx = (value) =>
        value == null ? '' : typeof value === 'string' ? value : String(JSON.stringify(value));
      const api = (globalThis.adf4j ??= {});
      Object.assign(api, {
        convert: (json, context) => JSON.parse(str(convertFn(String(json ?? ''), ctx(context)))),
        version: () => str(versionFn()),
        ready: true
      });
      if (typeof globalThis.__adf4jOnReady === 'function') {
        globalThis.__adf4jOnReady();
      }
      """)
  private static native void installApi(
      BiFunction<JSString, JSString, JSString> convertFn, Supplier<JSString> versionFn);

  private WasmBridge() {}
}
