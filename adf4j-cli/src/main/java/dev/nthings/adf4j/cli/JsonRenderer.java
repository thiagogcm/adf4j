package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.ParseResult;
import dev.nthings.adf4j.result.UnresolvedReferences;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/// Builds the CLI's JSON output trees from library result types. Deliberately a hand-written
/// projection, not databind over the records: a {@link Diagnostic}'s `cause` (`Throwable`) is
/// omitted so stack traces can't leak into stdout, and excerpt AST is rendered to Markdown rather
/// than dumped.
final class JsonRenderer {

  /// Every section key `analyze` can emit, in output order.
  static final List<String> METADATA_KEYS =
      List.of(
          "pageRefs",
          "externalRefs",
          "mentionRefs",
          "attachmentRefs",
          "referencedFileIds",
          "pageTreeRefs",
          "excerptRefs",
          "excerpts",
          "outline");

  static final Set<String> METADATA_KEY_SET = Set.copyOf(METADATA_KEYS);

  private final CliJson json;
  private final AdfToMarkdown converter;
  private final MarkdownOptions options;

  JsonRenderer(CliJson json, AdfToMarkdown converter, MarkdownOptions options) {
    this.json = json;
    this.converter = converter;
    this.options = options;
  }

  ObjectNode markdownResult(MarkdownResult result) {
    var node = json.object();
    node.put("body", result.body());
    node.put("wasLossy", result.wasLossy());
    node.set("diagnostics", diagnostics(result.diagnostics()));
    node.set("metadata", metadata(result.metadata(), METADATA_KEY_SET));
    node.set("unresolved", unresolved(result.unresolved()));
    return node;
  }

  ObjectNode parseResult(ParseResult result) {
    var node = json.object();
    node.put("validAdfRoot", result.validAdfRoot());
    node.set("issues", diagnostics(result.issues()));
    return node;
  }

  ObjectNode metadata(ContentMetadata metadata, Set<String> selected) {
    var node = json.object();
    if (selected.contains("pageRefs")) {
      node.set("pageRefs", strings(metadata.pageRefs().stream().map(p -> p.pageNodeId()).toList()));
    }
    if (selected.contains("externalRefs")) {
      node.set(
          "externalRefs", strings(metadata.externalRefs().stream().map(e -> e.url()).toList()));
    }
    if (selected.contains("mentionRefs")) {
      var array = json.array();
      for (var mention : metadata.mentionRefs()) {
        var item = json.object();
        item.put("id", mention.id());
        item.put("text", mention.text());
        array.add(item);
      }
      node.set("mentionRefs", array);
    }
    if (selected.contains("attachmentRefs")) {
      var array = json.array();
      for (var attachment : metadata.attachmentRefs()) {
        array.add(attachmentNode(attachment.fileId(), attachment.title(), attachment.mediaType()));
      }
      node.set("attachmentRefs", array);
    }
    if (selected.contains("referencedFileIds")) {
      node.set("referencedFileIds", strings(List.copyOf(metadata.referencedFileIds())));
    }
    if (selected.contains("pageTreeRefs")) {
      var array = json.array();
      for (var ref : metadata.pageTreeRefs()) {
        var item = json.object();
        item.put("macro", ref.macro().name().toLowerCase(java.util.Locale.ROOT));
        item.put("root", ref.root());
        ref.depth().ifPresent(depth -> item.put("depth", depth));
        item.put("all", ref.all());
        item.set("parameters", stringMap(ref.parameters()));
        array.add(item);
      }
      node.set("pageTreeRefs", array);
    }
    if (selected.contains("excerptRefs")) {
      var array = json.array();
      for (var ref : metadata.excerptRefs()) {
        var item = json.object();
        item.put("page", ref.page());
        item.put("excerptName", ref.excerptName());
        item.set("parameters", stringMap(ref.parameters()));
        array.add(item);
      }
      node.set("excerptRefs", array);
    }
    if (selected.contains("excerpts")) {
      var array = json.array();
      for (var excerpt : metadata.excerpts()) {
        var item = json.object();
        item.put("name", excerpt.name());
        item.put(
            "markdown", converter.convert(new AdfDocument(1, excerpt.content()), options).body());
        array.add(item);
      }
      node.set("excerpts", array);
    }
    if (selected.contains("outline")) {
      var array = json.array();
      for (var heading : metadata.outline()) {
        var item = json.object();
        item.put("level", heading.level());
        item.put("text", heading.text());
        item.put("anchor", heading.anchor());
        array.add(item);
      }
      node.set("outline", array);
    }
    return node;
  }

  ArrayNode diagnostics(List<Diagnostic> diagnostics) {
    var array = json.array();
    for (var diagnostic : diagnostics) {
      var item = json.object();
      item.put("code", diagnostic.code());
      item.put("severity", diagnostic.severity().name());
      item.put("message", diagnostic.message());
      array.add(item);
    }
    return array;
  }

  private ObjectNode unresolved(UnresolvedReferences unresolved) {
    var node = json.object();
    node.set("pageIds", strings(List.copyOf(unresolved.pageIds())));
    var pageTree = json.array();
    for (var ref : unresolved.pageTreeRefs()) {
      var item = json.object();
      item.put("macro", ref.macro().name().toLowerCase(java.util.Locale.ROOT));
      item.put("root", ref.root());
      pageTree.add(item);
    }
    node.set("pageTreeRefs", pageTree);
    var excerpts = json.array();
    for (var ref : unresolved.excerptRefs()) {
      var item = json.object();
      item.put("page", ref.page());
      item.put("excerptName", ref.excerptName());
      excerpts.add(item);
    }
    node.set("excerptRefs", excerpts);
    return node;
  }

  private ObjectNode attachmentNode(
      String fileId, @Nullable String title, @Nullable String mediaType) {
    var item = json.object();
    item.put("fileId", fileId);
    item.put("title", title);
    item.put("mediaType", mediaType);
    return item;
  }

  private ArrayNode strings(List<String> values) {
    var array = json.array();
    values.forEach(array::add);
    return array;
  }

  private ObjectNode stringMap(java.util.Map<String, String> values) {
    var node = json.object();
    values.forEach(node::put);
    return node;
  }
}
