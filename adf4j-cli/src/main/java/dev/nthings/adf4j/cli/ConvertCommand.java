package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.cli.JsonRenderer.RenderedExcerpt;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.UnknownNodeException;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import tools.jackson.databind.node.ObjectNode;

/// `adf4j convert`: renders ADF JSON as Markdown, or as a full JSON result envelope.
@CommandDefinition(
    name = "convert",
    description = "Convert ADF JSON to Markdown",
    generateHelp = true)
public class ConvertCommand extends Adf4jSubcommand {

  @Option(
      shortName = 'f',
      name = "format",
      description = "md (default) prints the body; json prints the full result",
      allowedValues = {"md", "json"})
  String format = "md";

  @Option(
      name = "fail-on-lossy",
      hasValue = false,
      description = "Exit 4 when the result is lossy (any WARNING/ERROR diagnostic)")
  boolean failOnLossy;

  @Mixin RenderingOptions rendering = new RenderingOptions();
  @Mixin ResolverOptions resolvers = new ResolverOptions();
  @Mixin AttachmentOptions attachments = new AttachmentOptions();

  @Override
  int run() {
    resolvers.warnVerbatim(common.quiet);

    var json = new CliJson();
    var parsed = parseInput();
    var options = RenderConfig.build(rendering, resolvers, json);
    var converter = AdfToMarkdown.with(options.withAttachments(attachments.read(json)));

    MarkdownResult result;
    String output;
    try {
      result = converter.convert(parsed);
      output =
          format.equals("json")
              ? json.write(jsonResult(result, converter), !common.compact) + "\n"
              : result.body();
    } catch (UnknownNodeException fail) { // UnknownNodePolicy.FAIL aborts
      throw new CliException(
          ExitCodes.CONTENT_FAILURE,
          "unknown node '" + fail.nodeType() + "' aborted the conversion (--unknown-nodes fail)",
          fail);
    }
    CliIo.writeOutput(common.output, output);

    printSummary(result.diagnostics(), result.wasLossy());
    if (!parsed.isValid()) {
      printDiagnostics(parsed.diagnostics());
      return ExitCodes.CONTENT_FAILURE;
    }
    if (failOnLossy && result.wasLossy()) {
      return ExitCodes.QUALITY_GATE;
    }
    return ExitCodes.OK;
  }

  private static ObjectNode jsonResult(MarkdownResult result, AdfToMarkdown converter) {
    var excerpts =
        result.metadata().excerpts().stream()
            .map(excerpt -> new RenderedExcerpt(excerpt.name(), converter.convert(excerpt).body()))
            .toList();
    return JsonRenderer.markdownResult(result, excerpts);
  }

  private void printSummary(List<Diagnostic> diagnostics, boolean lossy) {
    if (common.quiet || diagnostics.isEmpty()) {
      return;
    }
    var warnings = 0;
    var errors = 0;
    for (var diagnostic : diagnostics) {
      if (diagnostic.severity() == Diagnostic.Severity.WARNING) {
        warnings++;
      } else if (diagnostic.severity() == Diagnostic.Severity.ERROR) {
        errors++;
      }
    }
    System.err.println(
        "adf4j: " + warnings + " warning(s), " + errors + " error(s), lossy=" + lossy);
  }
}
