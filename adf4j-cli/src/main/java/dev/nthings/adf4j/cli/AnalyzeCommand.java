package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.options.ConversionOptions;
import java.util.List;
import java.util.StringJoiner;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/// `adf4j analyze`: extracts references, attachments, and the outline without rendering. Exits 3
/// on invalid content, still printing the output. `-f text` walks the same JSON.
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

  // Not allowedValues: annotations can't read the section table. selectedSections() validates.
  @OptionList(
      name = "select",
      description = "Comma-separated subset of sections to emit; defaults to every section")
  List<String> select = List.of();

  @Mixin AttachmentOptions attachments = new AttachmentOptions();

  @Override
  int run() {
    var selected = selectedSections();
    var json = new CliJson();
    var parsed = parseInput();
    var options = ConversionOptions.defaults().withAttachments(attachments.read(json));
    var metadata = AdfToMarkdown.with(options).analyze(parsed);

    var result = json.result(metadata);
    var sections = ((ObjectNode) result.get("metadata")).retain(selected);
    var output =
        format.equals("text") ? text(sections) : json.write(result, !common.compact) + "\n";
    CliIo.writeOutput(common.output, output);
    printDiagnostics(metadata.diagnostics());
    return metadata.isSuccess() ? ExitCodes.OK : ExitCodes.CONTENT_FAILURE;
  }

  private List<String> selectedSections() {
    for (var section : select) {
      if (!AdfJson.METADATA_SECTIONS.contains(section)) {
        throw CliException.usage(
            "Invalid value '%s' for option '--select'. Allowed values: %s"
                .formatted(section, String.join(", ", AdfJson.METADATA_SECTIONS)));
      }
    }
    return select.isEmpty() ? AdfJson.METADATA_SECTIONS : select;
  }

  private static String text(ObjectNode sections) {
    var sb = new StringBuilder();
    for (var section : sections.properties()) {
      var items = section.getValue();
      sb.append(section.getKey()).append(" (").append(items.size()).append("):");
      sb.append(items.isEmpty() ? " none\n" : "\n");
      for (var item : items) {
        sb.append("  ").append(item.isObject() ? fields(item) : value(item)).append('\n');
      }
    }
    return sb.toString();
  }

  private static String fields(JsonNode object) {
    var fields = new StringJoiner(" ");
    for (var field : object.properties()) {
      fields.add(field.getKey() + "=" + value(field.getValue()));
    }
    return fields.toString();
  }

  private static String value(JsonNode value) {
    if (value.isObject()) {
      return "{" + fields(value) + "}";
    }
    return value.isNull() ? "null" : value.asString();
  }
}
