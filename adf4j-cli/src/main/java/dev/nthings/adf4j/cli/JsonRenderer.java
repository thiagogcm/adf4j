package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.metadata.MentionReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.ParseResult;
import dev.nthings.adf4j.result.UnresolvedReferences;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/// Projects library result types onto the CLI's JSON output trees. Deliberately a hand-written
/// projection, not databind over the records: a {@link Diagnostic}'s `cause` (`Throwable`) is
/// omitted so stack traces can't leak into stdout, and excerpt AST is not dumped. Rendering is the
/// caller's job, so nothing here needs a converter or options.
final class JsonRenderer {

  /// An excerpt the caller rendered, as `convert -f json` reports it.
  record RenderedExcerpt(@Nullable String name, String markdown) {}

  private record Section(String name, Function<ContentMetadata, JsonNode> project) {}

  // The table behind `analyze --select`, `analyze -f text` and the `metadata` object of
  // `convert -f json`: one row per section, in output order.
  private static final List<Section> SECTIONS =
      List.of(
          new Section("pageIds", metadata -> strings(metadata.pageIds())),
          new Section("externalUrls", metadata -> strings(metadata.externalUrls())),
          new Section("mentionRefs", metadata -> nodes(metadata.mentionRefs(), JsonRenderer::node)),
          new Section(
              "attachmentRefs", metadata -> nodes(metadata.attachmentRefs(), JsonRenderer::node)),
          new Section("referencedFileIds", metadata -> strings(metadata.referencedFileIds())),
          new Section(
              "pageTreeRefs", metadata -> nodes(metadata.pageTreeRefs(), JsonRenderer::node)),
          new Section("excerptRefs", metadata -> nodes(metadata.excerptRefs(), JsonRenderer::node)),
          new Section("excerpts", metadata -> nodes(metadata.excerpts(), JsonRenderer::node)),
          new Section("outline", metadata -> nodes(metadata.outline(), JsonRenderer::node)));

  /// Every section `analyze` can emit, in output order.
  static final List<String> SECTION_NAMES = SECTIONS.stream().map(Section::name).toList();

  private JsonRenderer() {}

  /// The `convert -f json` result; `excerpts` adds each excerpt's Markdown to its `metadata` entry.
  static ObjectNode markdownResult(MarkdownResult result, List<RenderedExcerpt> excerpts) {
    var metadata = metadata(result.metadata(), SECTION_NAMES);
    metadata.set("excerpts", nodes(excerpts, JsonRenderer::node));
    var node = CliJson.object();
    node.put("body", result.body());
    node.put("wasLossy", result.wasLossy());
    node.set("diagnostics", diagnostics(result.diagnostics()));
    node.set("metadata", metadata);
    node.set("unresolved", node(result.unresolved()));
    return node;
  }

  /// The `validate -f json` result.
  static ObjectNode parseResult(ParseResult result) {
    var node = CliJson.object();
    node.put("valid", result.isValid());
    node.set("diagnostics", diagnostics(result.diagnostics()));
    return node;
  }

  /// The selected sections of `metadata`, in output order.
  static ObjectNode metadata(ContentMetadata metadata, Collection<String> selected) {
    var node = CliJson.object();
    for (var section : SECTIONS) {
      if (selected.contains(section.name())) {
        node.set(section.name(), section.project().apply(metadata));
      }
    }
    return node;
  }

  private static ArrayNode diagnostics(List<Diagnostic> diagnostics) {
    return nodes(
        diagnostics,
        diagnostic -> {
          var item = CliJson.object();
          item.put("code", diagnostic.code().name());
          item.put("severity", diagnostic.severity().name());
          item.put("message", diagnostic.message());
          return item;
        });
  }

  private static ObjectNode node(UnresolvedReferences unresolved) {
    var node = CliJson.object();
    node.set("pageIds", strings(unresolved.pageIds()));
    node.set("pageTreeRefs", nodes(unresolved.pageTreeRefs(), JsonRenderer::node));
    node.set("excerptRefs", nodes(unresolved.excerptRefs(), JsonRenderer::node));
    return node;
  }

  private static ObjectNode node(MentionReference mention) {
    var item = CliJson.object();
    item.put("id", mention.id());
    item.put("text", mention.text());
    return item;
  }

  private static ObjectNode node(AttachmentReference attachment) {
    var item = CliJson.object();
    item.put("fileId", attachment.fileId());
    item.put("title", attachment.title());
    item.put("mediaType", attachment.mediaType());
    item.put("downloadUrl", attachment.downloadUrl());
    return item;
  }

  private static ObjectNode node(PageTreeReference reference) {
    var item = CliJson.object();
    item.put("macro", CliJson.kebabCase(reference.macro()));
    item.put("root", reference.root());
    reference.depth().ifPresent(depth -> item.put("depth", depth));
    item.put("all", reference.all());
    item.set("parameters", stringMap(reference.parameters()));
    return item;
  }

  private static ObjectNode node(ExcerptIncludeReference reference) {
    var item = CliJson.object();
    item.put("page", reference.page());
    item.put("excerptName", reference.excerptName());
    item.set("parameters", stringMap(reference.parameters()));
    return item;
  }

  private static ObjectNode node(ExcerptDefinition excerpt) {
    var item = CliJson.object();
    item.put("name", excerpt.name());
    return item;
  }

  private static ObjectNode node(RenderedExcerpt excerpt) {
    var item = CliJson.object();
    item.put("name", excerpt.name());
    item.put("markdown", excerpt.markdown());
    return item;
  }

  private static ObjectNode node(HeadingReference heading) {
    var item = CliJson.object();
    item.put("level", heading.level());
    item.put("text", heading.text());
    item.put("anchor", heading.anchor());
    return item;
  }

  private static <T> ArrayNode nodes(Collection<T> values, Function<T, ObjectNode> project) {
    var array = CliJson.array();
    values.forEach(value -> array.add(project.apply(value)));
    return array;
  }

  private static ArrayNode strings(Collection<String> values) {
    var array = CliJson.array();
    values.forEach(array::add);
    return array;
  }

  private static ObjectNode stringMap(Map<String, String> values) {
    var node = CliJson.object();
    values.forEach(node::put);
    return node;
  }
}
