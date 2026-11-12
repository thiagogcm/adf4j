package dev.nthings.adf4j.internal.json;

import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.metadata.MentionReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.result.AnalysisResult;
import dev.nthings.adf4j.result.ConversionEffect;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.OperationResult;
import dev.nthings.adf4j.result.ParseResult;
import dev.nthings.adf4j.result.UnresolvedResource;
import dev.nthings.adf4j.result.ValidationResult;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/// JSON projection shared by library, CLI and WASM results. Throwable causes and excerpt AST
/// content stay Java-only.
public final class ResultJson {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  // `\n` newlines, not System.lineSeparator(), so pretty output matches across platforms.
  private static final DefaultPrettyPrinter PRETTY =
      new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n"));

  /// Keys in documented order: `ok`, `body`, `hasWarnings`, `diagnostics`, `metadata`,
  /// `effects`, `unresolved`, `error`; each only for result types that carry it.
  public static String write(OperationResult result, boolean pretty) {
    var output = JSON.createObjectNode();
    output.put("ok", result.isSuccess());
    if (result instanceof MarkdownResult converted) {
      output.put("body", converted.body());
    }
    output.put("hasWarnings", result.hasWarnings());
    output.set("diagnostics", diagnostics(result.diagnostics()));
    switch (result) {
      case MarkdownResult converted -> {
        output.set("metadata", metadata(converted.metadata()));
        output.set("effects", nodes(converted.effects(), ResultJson::node));
        output.set("unresolved", nodes(converted.unresolved(), ResultJson::node));
      }
      case AnalysisResult analyzed -> output.set("metadata", metadata(analyzed.metadata()));
      case ParseResult _, ValidationResult _ -> {}
    }
    if (!result.isSuccess()) {
      output.put("error", result.errorMessage());
    }
    return pretty
        ? JSON.writer().with(PRETTY).writeValueAsString(output)
        : JSON.writeValueAsString(output);
  }

  private record Section(String name, Function<ContentMetadata, JsonNode> project) {}

  private static final List<Section> SECTIONS =
      List.of(
          new Section("pageIds", metadata -> strings(metadata.pageIds())),
          new Section("externalUrls", metadata -> strings(metadata.externalUrls())),
          new Section("mentionRefs", metadata -> nodes(metadata.mentionRefs(), ResultJson::node)),
          new Section(
              "attachmentRefs", metadata -> nodes(metadata.attachmentRefs(), ResultJson::node)),
          new Section("referencedFileIds", metadata -> strings(metadata.referencedFileIds())),
          new Section("pageTreeRefs", metadata -> nodes(metadata.pageTreeRefs(), ResultJson::node)),
          new Section("excerptRefs", metadata -> nodes(metadata.excerptRefs(), ResultJson::node)),
          new Section("excerpts", metadata -> nodes(metadata.excerpts(), ResultJson::node)),
          new Section("outline", metadata -> nodes(metadata.outline(), ResultJson::node)));

  /// Keys of the `metadata` object, in output order.
  public static final List<String> SECTION_NAMES = SECTIONS.stream().map(Section::name).toList();

  private ResultJson() {}

  private static ObjectNode metadata(ContentMetadata metadata) {
    var node = JSON.createObjectNode();
    for (var section : SECTIONS) {
      node.set(section.name(), section.project().apply(metadata));
    }
    return node;
  }

  private static ArrayNode diagnostics(List<Diagnostic> diagnostics) {
    return nodes(
        diagnostics,
        diagnostic -> {
          var item = JSON.createObjectNode();
          item.put("code", diagnostic.code().name());
          item.put("severity", diagnostic.severity().name());
          item.put("message", diagnostic.message());
          item.set("paths", strings(diagnostic.paths()));
          var details = item.putObject("details");
          diagnostic.details().forEach(details::put);
          return item;
        });
  }

  private static ObjectNode node(ConversionEffect effect) {
    return JSON.createObjectNode()
        .put("kind", effect.kind().name())
        .put("path", effect.path())
        .put("message", effect.message());
  }

  private static ObjectNode node(UnresolvedResource resource) {
    return JSON.createObjectNode()
        .put("kind", resource.kind().name())
        .put("id", resource.id())
        .put("path", resource.path())
        .put("reason", resource.reason().name())
        .put("fallback", resource.fallback());
  }

  private static ObjectNode node(MentionReference mention) {
    var item = JSON.createObjectNode();
    item.put("id", mention.id());
    item.put("text", mention.text());
    return item;
  }

  private static ObjectNode node(AttachmentReference attachment) {
    var item = JSON.createObjectNode();
    item.put("fileId", attachment.fileId());
    item.put("title", attachment.title());
    item.put("mediaType", attachment.mediaType());
    item.put("downloadUrl", attachment.downloadUrl());
    return item;
  }

  private static ObjectNode node(PageTreeReference reference) {
    var item = JSON.createObjectNode();
    item.put("macro", Strings.kebabCase(reference.macro()));
    item.put("root", reference.root());
    reference.depth().ifPresent(depth -> item.put("depth", depth));
    item.put("all", reference.all());
    item.set("parameters", stringMap(reference.parameters()));
    return item;
  }

  private static ObjectNode node(ExcerptIncludeReference reference) {
    var item = JSON.createObjectNode();
    item.put("page", reference.page());
    item.put("excerptName", reference.excerptName());
    item.set("parameters", stringMap(reference.parameters()));
    return item;
  }

  private static ObjectNode node(ExcerptDefinition excerpt) {
    var item = JSON.createObjectNode();
    item.put("name", excerpt.name());
    return item;
  }

  private static ObjectNode node(HeadingReference heading) {
    var item = JSON.createObjectNode();
    item.put("level", heading.level());
    item.put("text", heading.text());
    item.put("anchor", heading.anchor());
    return item;
  }

  private static <T> ArrayNode nodes(Collection<T> values, Function<T, ObjectNode> project) {
    var array = JSON.createArrayNode();
    values.forEach(value -> array.add(project.apply(value)));
    return array;
  }

  private static ArrayNode strings(Collection<String> values) {
    var array = JSON.createArrayNode();
    values.forEach(array::add);
    return array;
  }

  private static ObjectNode stringMap(Map<String, String> values) {
    var node = JSON.createObjectNode();
    values.forEach(node::put);
    return node;
  }
}
