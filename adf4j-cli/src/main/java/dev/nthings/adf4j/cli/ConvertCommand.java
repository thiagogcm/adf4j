package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

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

  @Override
  int run() {
    resolvers.warnVerbatim(common.quiet);

    var json = new CliJson();
    var input = CliIo.readInput(file);
    var options = RenderConfig.build(rendering, resolvers, json);
    var converter = AdfToMarkdown.with(options);

    MarkdownResult result;
    try {
      result = converter.convert(input);
    } catch (IllegalStateException fail) { // UnknownNodePolicy.FAIL aborts
      throw new CliException(
          ExitCodes.CONTENT_FAILURE,
          "conversion aborted by --unknown-nodes fail: " + fail.getMessage(),
          fail);
    }

    String output;
    if (format.equals("json")) {
      var node = new JsonRenderer(json, converter, options).markdownResult(result);
      output = json.write(node, !common.compact) + "\n";
    } else {
      output = result.body();
    }
    CliIo.writeOutput(common.output, output);

    printSummary(result.diagnostics(), result.wasLossy());
    if (failOnLossy && result.wasLossy()) {
      return ExitCodes.QUALITY_GATE;
    }
    return ExitCodes.OK;
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
