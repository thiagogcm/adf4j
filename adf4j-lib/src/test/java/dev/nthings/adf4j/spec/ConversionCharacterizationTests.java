package dev.nthings.adf4j.spec;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.AdfExtension;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.ExtensionOutput;
import dev.nthings.adf4j.options.ExtensionRenderer;
import dev.nthings.adf4j.options.PageTreeEntry;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;
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

/// Pins everything observable about a conversion (body, warnings, diagnostics, metadata,
/// unresolved references) across the spec fixtures and an off-spec corpus, under an option matrix.
/// The `.md` goldens cover only the default body of schema-shaped input, so changes to coercion,
/// positional behaviour or mark handling can pass them unnoticed; this suite cannot be fooled that
/// way. To accept an intended change, run with `-Dadf4j.updateCharacterization=true` and review the
/// diff of `adf/characterization`.
class ConversionCharacterizationTests {

  private static final String ROOT = "adf/characterization/";
  private static final Path SOURCE_ROOT = Path.of("src/test/resources", ROOT);

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
  void conversions_match_the_recorded_characterization() throws IOException {
    var cases = cases();
    var actual = new LinkedHashMap<String, Map<String, String>>();
    for (var preset : PRESETS.entrySet()) {
      var entries = new LinkedHashMap<String, String>();
      for (var entry : cases.entrySet()) {
        entries.put(entry.getKey(), characterize(entry.getValue(), preset.getValue()));
      }
      actual.put(preset.getKey(), entries);
    }

    if (Boolean.getBoolean("adf4j.updateCharacterization")) {
      Files.createDirectories(SOURCE_ROOT);
      for (var preset : actual.keySet()) {
        Files.writeString(SOURCE_ROOT.resolve(preset + ".txt"), render(actual, preset));
      }
    }

    for (var preset : actual.keySet()) {
      assertThat(render(actual, preset))
          .as("characterization of preset %s", preset)
          .isEqualTo(TestResources.read(ROOT + preset + ".txt"));
    }
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

  // Fixture files and the off-spec corpus, keyed by a stable name.
  private static Map<String, String> cases() throws IOException {
    var cases = new TreeMap<String, String>();
    for (var file : TestResources.specFixtures()) {
      cases.put(file.substring("adf/spec/".length()), TestResources.read(file));
    }
    cases.putAll(TestResources.corpus());
    return cases;
  }

  private static String characterize(String adf, ConversionOptions options) {
    var builder = new StringBuilder();
    try {
      var converter = AdfToMarkdown.with(options);
      var result = converter.convert(adf);
      line(builder, "body", result.body());
      line(builder, "warnings", result.hasWarnings());
      for (var diagnostic : result.diagnostics()) {
        line(
            builder,
            "diagnostic",
            diagnostic.code() + "|" + diagnostic.severity() + "|" + diagnostic.message());
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
    if (!text.isEmpty()) builder.append(' ').append(text);
    builder.append('\n');
  }

  // A deterministic rendering of records, maps and lists: some maps use salted iteration order, so
  // default toString would differ between JVM runs.
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
