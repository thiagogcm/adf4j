package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.MarkdownOptions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

/// `adf4j analyze`: extracts references, attachments, and the outline without rendering a body.
/// Rendering/resolver options still apply because excerpt sections render through the converter.
@CommandDefinition(
    name = "analyze",
    description = "Extract references, attachments, and outline",
    generateHelp = true)
public class AnalyzeCommand extends Adf4jSubcommand {

  @Option(
      shortName = 'f',
      name = "format",
      description = "json (default) or human-readable text",
      allowedValues = {"json", "text"})
  String format = "json";

  @OptionList(
      name = "select",
      description = "Comma-separated subset of sections to emit; defaults to every section",
      allowedValues = {
        "pageRefs",
        "externalRefs",
        "mentionRefs",
        "attachmentRefs",
        "referencedFileIds",
        "pageTreeRefs",
        "excerptRefs",
        "excerpts",
        "outline"
      })
  List<String> select = List.of();

  @Mixin RenderingOptions rendering = new RenderingOptions();
  @Mixin ResolverOptions resolvers = new ResolverOptions();

  @Override
  int run() {
    resolvers.warnVerbatim(common.quiet);
    // The parser already rejected values outside allowedValues, so this only dedupes.
    var selected = select.isEmpty() ? JsonRenderer.METADATA_KEY_SET : new LinkedHashSet<>(select);

    var json = new CliJson();
    var input = CliIo.readInput(file);
    var options = RenderConfig.build(rendering, resolvers, json);
    var converter = AdfToMarkdown.with(options);
    var metadata = converter.analyze(input);

    String output;
    if (format.equals("text")) {
      output = analyzeText(metadata, selected, converter, options);
    } else {
      var renderer = new JsonRenderer(json, converter, options);
      output = json.write(renderer.metadata(metadata, selected), !common.compact) + "\n";
    }
    CliIo.writeOutput(common.output, output);
    return ExitCodes.OK;
  }

  private String analyzeText(
      ContentMetadata metadata,
      Set<String> selected,
      AdfToMarkdown converter,
      MarkdownOptions options) {
    var sb = new StringBuilder();
    if (selected.contains("outline")) {
      sb.append("outline:\n");
      for (var heading : metadata.outline()) {
        sb.append("  ")
            .append("  ".repeat(Math.max(0, heading.level() - 1)))
            .append("H")
            .append(heading.level())
            .append(' ')
            .append(heading.text())
            .append(" (#")
            .append(heading.anchor())
            .append(")\n");
      }
    }
    if (selected.contains("pageRefs")) {
      appendList(sb, "pageRefs", metadata.pageRefs().stream().map(p -> p.pageNodeId()).toList());
    }
    if (selected.contains("externalRefs")) {
      appendList(sb, "externalRefs", metadata.externalRefs().stream().map(e -> e.url()).toList());
    }
    if (selected.contains("mentionRefs")) {
      appendList(
          sb,
          "mentionRefs",
          metadata.mentionRefs().stream().map(m -> m.text() + " [" + m.id() + "]").toList());
    }
    if (selected.contains("attachmentRefs")) {
      appendList(
          sb,
          "attachmentRefs",
          metadata.attachmentRefs().stream()
              .map(a -> a.title() + " [" + a.fileId() + "]")
              .toList());
    }
    if (selected.contains("referencedFileIds")) {
      appendList(sb, "referencedFileIds", List.copyOf(metadata.referencedFileIds()));
    }
    if (selected.contains("pageTreeRefs")) {
      appendList(
          sb,
          "pageTreeRefs",
          metadata.pageTreeRefs().stream()
              .map(r -> r.macro().name().toLowerCase(Locale.ROOT) + " root=" + r.root())
              .toList());
    }
    if (selected.contains("excerptRefs")) {
      appendList(
          sb,
          "excerptRefs",
          metadata.excerptRefs().stream()
              .map(r -> r.page() + (r.excerptName() == null ? "" : "/" + r.excerptName()))
              .toList());
    }
    if (selected.contains("excerpts")) {
      appendList(
          sb,
          "excerpts",
          metadata.excerpts().stream()
              .map(
                  e ->
                      (e.name() == null ? "(unnamed)" : e.name())
                          + ": "
                          + converter
                              .convert(new AdfDocument(1, e.content()), options)
                              .body()
                              .strip())
              .toList());
    }
    return sb.toString();
  }

  private static void appendList(StringBuilder sb, String label, List<String> values) {
    sb.append(label).append(" (").append(values.size()).append("):");
    if (values.isEmpty()) {
      sb.append(" none\n");
      return;
    }
    sb.append('\n');
    for (var value : values) {
      sb.append("  ").append(value).append('\n');
    }
  }
}
