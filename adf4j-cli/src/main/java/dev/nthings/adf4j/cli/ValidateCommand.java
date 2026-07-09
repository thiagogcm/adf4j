package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.ParseResult;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;

/// `adf4j validate`: parse-checks ADF JSON and reports diagnostics. Exit codes: 0 valid;
/// 3 invalid root or an ERROR diagnostic; 4 when `--fail-on-warning` trips.
@CommandDefinition(
    name = "validate",
    description = "Parse-check ADF JSON and report diagnostics",
    generateHelp = true)
public class ValidateCommand extends Adf4jSubcommand {

  @Option(
      shortName = 'f',
      name = "format",
      description = "text (default) or json {validAdfRoot, issues}",
      allowedValues = {"text", "json"})
  String format = "text";

  @Option(
      name = "fail-on-warning",
      hasValue = false,
      description = "Exit 4 when a WARNING is present (else 0 on a valid root)")
  boolean failOnWarning;

  @Override
  int run() {
    var input = CliIo.readInput(file);
    var converter = AdfToMarkdown.create();
    var parsed = converter.parse(input);
    var hasError = false;
    var hasWarning = false;
    for (var issue : parsed.issues()) {
      hasError |= issue.severity() == Diagnostic.Severity.ERROR;
      hasWarning |= issue.severity() == Diagnostic.Severity.WARNING;
    }

    String output;
    if (format.equals("json")) {
      var json = new CliJson();
      var renderer = new JsonRenderer(json, converter, MarkdownOptions.defaults());
      output = json.write(renderer.parseResult(parsed), !common.compact) + "\n";
    } else {
      output = validateText(parsed);
    }
    CliIo.writeOutput(common.output, output);

    if (!parsed.validAdfRoot() || hasError) {
      return ExitCodes.CONTENT_FAILURE;
    }
    if (failOnWarning && hasWarning) {
      return ExitCodes.QUALITY_GATE;
    }
    return ExitCodes.OK;
  }

  private String validateText(ParseResult parsed) {
    var sb = new StringBuilder();
    sb.append("valid: ").append(parsed.validAdfRoot()).append('\n');
    if (parsed.issues().isEmpty()) {
      sb.append("issues: none\n");
      return sb.toString();
    }
    sb.append("issues:\n");
    for (var issue : parsed.issues()) {
      sb.append("  [")
          .append(issue.severity())
          .append("] ")
          .append(issue.code())
          .append(": ")
          .append(issue.message())
          .append('\n');
    }
    return sb.toString();
  }
}
