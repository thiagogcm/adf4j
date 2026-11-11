package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

/// `adf4j convert`: renders ADF JSON as Markdown, or a JSON result with `-f json`. Exits 3 on
/// invalid content (stdout is still written) and 4 when a `--fail-on-*` gate trips.
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
      name = "fail-on-warning",
      hasValue = false,
      description = "Exit 4 when conversion has warnings")
  boolean failOnWarning;

  @Option(
      name = "fail-on-changes",
      hasValue = false,
      description = "Exit 4 when conversion changes content, structure, or styling")
  boolean failOnChanges;

  @Option(
      name = "fail-on-unresolved",
      hasValue = false,
      description = "Exit 4 when output has unresolved dependencies")
  boolean failOnUnresolved;

  @Mixin RenderingOptions rendering = new RenderingOptions();
  @Mixin ResolverOptions resolvers = new ResolverOptions();
  @Mixin AttachmentOptions attachments = new AttachmentOptions();

  @Override
  int run() {
    resolvers.warnVerbatim(common.quiet);

    var json = new CliJson();
    var parsed = parseInput();
    var options = RenderConfig.build(rendering, resolvers, json);
    if (attachments.path != null) {
      options = options.withAttachments(attachments.read(json));
    }
    var converter = AdfToMarkdown.with(options);

    var result = converter.convert(parsed);
    var output =
        format.equals("json") ? AdfJson.writeResult(result, !common.compact) + "\n" : result.body();
    CliIo.writeOutput(common.output, output);

    printSummary(result.diagnostics());
    if (!result.isSuccess()) {
      printDiagnostics(result.diagnostics());
      return ExitCodes.CONTENT_FAILURE;
    }
    if ((failOnWarning && result.hasWarnings())
        || (failOnChanges && !result.effects().isEmpty())
        || (failOnUnresolved && !result.unresolved().isEmpty())) {
      return ExitCodes.QUALITY_GATE;
    }
    return ExitCodes.OK;
  }

  private void printSummary(List<Diagnostic> diagnostics) {
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
    System.err.println("adf4j: " + warnings + " warning(s), " + errors + " error(s)");
  }
}
