package dev.nthings.adf4j.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.AdfExtension;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.ExtensionOutput;
import dev.nthings.adf4j.options.ExtensionRenderer;
import dev.nthings.adf4j.options.PageTreeEntry;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.OperationResult;
import dev.nthings.adf4j.testing.TestResources;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.jackson.databind.json.JsonMapper;

/// The golden layer: every fixture under `adf/spec` and every off-spec case in
// `adf/golden/corpus.tsv`,
/// converted under an option matrix, with everything observable pinned: body, diagnostics with
// paths
/// and details, effects, unresolved dependencies, metadata, excerpts, and whether the written
/// document conforms to the ADF schema. A fixture's sibling `.md` holds its default body in
// readable
/// form. Every result must also serialize to JSON that conforms to `operation-result.schema.json`.
///
/// To accept an intended change, run with `-Dadf4j.updateGoldens=true` and review the diff.
class ConversionGoldenTests {

  private static final String GOLDEN = "adf/golden/";
  private static final Path SOURCE = Path.of("src/test/resources");
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final boolean UPDATE = Boolean.getBoolean("adf4j.updateGoldens");

  // Non-default presets store only the cases that differ from `defaults`, which keeps them small.
  private static final Map<String, ConversionOptions> PRESETS = presets();

  private static Map<String, ConversionOptions> presets() {
    var presets = new LinkedHashMap<String, ConversionOptions>();
    presets.put("defaults", ConversionOptions.defaults());
    presets.put(
        "visual",
        ConversionOptions.defaults()
            .withHtmlVisualMarks(true)
            .withImageSizeAttributes(true)
            .withTableFallback(TableFallback.HTML));
    presets.put(
        "layout",
        ConversionOptions.defaults()
            .withTableFallback(TableFallback.GFM_EMPTY_HEADER)
            .withCollapseHardBreaks(true)
            .withEscapeParentheses(true));
    presets.put(
        "unknown-skip", ConversionOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.SKIP));
    presets.put(
        "unknown-raw",
        ConversionOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.PRESERVE_RAW));
    presets.put(
        "unknown-fail", ConversionOptions.defaults().withUnknownNodePolicy(UnknownNodePolicy.FAIL));
    presets.put("resolvers", resolvers());
    return presets;
  }

  private static ConversionOptions resolvers() {
    ExtensionRenderer dumpingRenderer =
        extension ->
            "x".equals(extension.extensionType())
                ? ExtensionOutput.header(describe(ExtensionView.of(extension)))
                : ExtensionOutput.defer();
    return ConversionOptions.defaults()
        .withMediaResolver(media -> "https://media.example/" + media.id())
        .withAttachmentResolver(reference -> "https://attach.example/" + reference.fileId())
        .withPageLinkResolver(id -> "https://page.example/" + id)
        .withPageTreeResolver(reference -> List.of(new PageTreeEntry(0, "Child", "1")))
        .withExcerptResolver(reference -> "excerpt:" + reference.page())
        .withExtensionRenderers(List.of(dumpingRenderer))
        .withAttachments(List.of(new AttachmentReference("1", "a.pdf", "application/pdf")));
  }

  @Test
  void conversions_match_their_goldens() throws IOException {
    var cases = cases();
    var adfSchema = schema("../docs/spec/adf-schema.json", SpecificationVersion.DRAFT_4);
    var resultSchema =
        schema("../docs/schemas/operation-result.schema.json", SpecificationVersion.DRAFT_2020_12);
    var contract = new ArrayList<String>();
    var actual = new LinkedHashMap<String, Map<String, String>>();
    var bodies = new TreeMap<String, String>();
    for (var preset : PRESETS.entrySet()) {
      var converter = AdfToMarkdown.with(preset.getValue());
      var entries = new LinkedHashMap<String, String>();
      for (var entry : cases.entrySet()) {
        var name = entry.getKey();
        var adf = entry.getValue();
        entries.put(name, characterize(converter, adf, adfSchema));
        checkContract(resultSchema, preset.getKey() + " " + name, converter, adf, contract);
        if (preset.getKey().equals("defaults") && name.endsWith(".json")) {
          bodies.put(name, converter.convert(adf).body());
        }
      }
      actual.put(preset.getKey(), entries);
    }

    if (UPDATE) {
      write(actual, bodies);
    }

    var checks = new ArrayList<Executable>();
    checks.add(() -> assertThat(contract).as("results violating the JSON contract").isEmpty());
    for (var preset : actual.keySet()) {
      var expected = TestResources.read(GOLDEN + preset + ".txt");
      checks.add(
          () -> assertThat(render(actual, preset)).as("preset %s", preset).isEqualTo(expected));
    }
    for (var body : bodies.entrySet()) {
      var markdown = markdownPath(body.getKey());
      if (TestResources.exists(markdown)) {
        var expected = TestResources.stripFinalNewline(TestResources.read(markdown));
        checks.add(
            () ->
                assertThat(body.getValue())
                    .as("default body of %s", body.getKey())
                    .isEqualToNormalizingNewlines(expected));
      }
    }
    assertAll(checks);
  }

  private static void write(Map<String, Map<String, String>> actual, Map<String, String> bodies)
      throws IOException {
    for (var preset : actual.keySet()) {
      Files.writeString(SOURCE.resolve(GOLDEN + preset + ".txt"), render(actual, preset));
    }
    for (var body : bodies.entrySet()) {
      var markdown = SOURCE.resolve(markdownPath(body.getKey()));
      if (Files.exists(markdown)) {
        Files.writeString(markdown, body.getValue() + "\n");
      }
    }
  }

  private static String markdownPath(String fixture) {
    return "adf/spec/" + fixture.substring(0, fixture.length() - ".json".length()) + ".md";
  }

  private static String render(Map<String, Map<String, String>> actual, String preset) {
    var defaults = actual.get("defaults");
    var builder = new StringBuilder();
    for (var entry : actual.get(preset).entrySet()) {
      if (preset.equals("defaults") || !entry.getValue().equals(defaults.get(entry.getKey()))) {
        builder.append("### ").append(entry.getKey()).append('\n').append(entry.getValue());
      }
    }
    return builder.toString();
  }

  // Spec fixtures keep their `.json` suffix; corpus cases are bare names.
  private static Map<String, String> cases() throws IOException {
    var cases = new TreeMap<String, String>();
    for (var file : TestResources.specFixtures()) {
      cases.put(file.substring("adf/spec/".length()), TestResources.read(file));
    }
    cases.putAll(TestResources.corpus());
    return cases;
  }

  private static String characterize(AdfToMarkdown converter, String adf, Schema adfSchema) {
    var builder = new StringBuilder();
    var parsed = AdfJson.parse(adf);
    if (parsed.document() != null) {
      var written = JSON.readTree(AdfJson.write(parsed.document()));
      line(builder, "schema", adfSchema.validate(written).isEmpty() ? "conforms" : "off-schema");
    }
    try {
      var result = converter.convert(adf);
      line(builder, "body", result.body());
      line(builder, "warnings", result.hasWarnings());
      for (var diagnostic : result.diagnostics()) {
        line(
            builder,
            "diagnostic",
            String.join(
                "|",
                diagnostic.code().name(),
                diagnostic.severity().name(),
                diagnostic.message(),
                describe(diagnostic.paths()),
                describe(diagnostic.details())));
      }
      for (var effect : result.effects()) {
        line(builder, "effect", effect.kind() + "|" + effect.path() + "|" + effect.message());
      }
      line(builder, "unresolved", describe(result.unresolved()));
      var metadata = result.metadata();
      line(builder, "pageIds", describe(metadata.pageIds()));
      line(builder, "externalUrls", describe(metadata.externalUrls()));
      line(builder, "mentionRefs", describe(metadata.mentionRefs()));
      line(builder, "attachmentRefs", describe(metadata.attachmentRefs()));
      line(builder, "pageTreeRefs", describe(metadata.pageTreeRefs()));
      line(builder, "excerptRefs", describe(metadata.excerptRefs()));
      line(builder, "outline", describe(metadata.outline()));
      for (var excerpt : metadata.excerpts()) {
        line(builder, "excerpt", excerpt.name() + ":" + converter.convert(excerpt).body());
      }
    } catch (RuntimeException failure) {
      line(builder, "threw", failure.getClass().getName() + ": " + failure.getMessage());
    }
    return builder.toString();
  }

  // Convert and analyze results alike must serialize to the published result schema.
  private static void checkContract(
      Schema schema, String label, AdfToMarkdown converter, String adf, List<String> violations) {
    for (OperationResult result : List.of(converter.convert(adf), converter.analyze(adf))) {
      var errors = schema.validate(JSON.readTree(AdfJson.writeResult(result)));
      if (!errors.isEmpty()) {
        violations.add(label + ": " + errors);
      }
    }
  }

  private static Schema schema(String path, SpecificationVersion version) throws IOException {
    return SchemaRegistry.withDefaultDialect(version).getSchema(Files.readString(Path.of(path)));
  }

  // What an extension renderer reads, kept apart from `attrs` so the dump pins the accessors.
  private record ExtensionView(
      String extensionType,
      String extensionKey,
      String text,
      Object macroParams,
      Object parameters) {

    static ExtensionView of(AdfExtension extension) {
      return new ExtensionView(
          extension.extensionType(),
          extension.extensionKey(),
          extension.text(),
          extension.macroParams().values(),
          extension.parameters());
    }
  }

  private static void line(StringBuilder builder, String key, Object value) {
    var text = String.valueOf(value).replace("\\", "\\\\").replace("\n", "\\n");
    builder.append(key).append(":");
    if (!text.isEmpty()) {
      builder.append(' ').append(text);
    }
    builder.append('\n');
  }

  // Deterministic rendering: some maps use salted iteration order, so toString varies per JVM.
  private static String describe(Object value) {
    return switch (value) {
      case null -> "null";
      case Map<?, ?> map -> {
        var sorted = new TreeMap<String, String>();
        map.forEach((key, item) -> sorted.put(String.valueOf(key), describe(item)));
        yield sorted.toString();
      }
      case Collection<?> items -> {
        var described = new ArrayList<String>();
        items.forEach(item -> described.add(describe(item)));
        yield described.toString();
      }
      case Record record -> describeRecord(record);
      default -> value.toString();
    };
  }

  private static String describeRecord(Record record) {
    var fields = new LinkedHashMap<String, String>();
    for (var component : record.getClass().getRecordComponents()) {
      try {
        var accessor = component.getAccessor();
        accessor.setAccessible(true);
        fields.put(component.getName(), describe(accessor.invoke(record)));
      } catch (IllegalAccessException | InvocationTargetException failure) {
        throw new IllegalStateException(failure);
      }
    }
    return record.getClass().getSimpleName() + fields;
  }
}
