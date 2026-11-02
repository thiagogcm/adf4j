package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.ParseResult;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;

/// `adf4j validate`: parse-checks ADF JSON and reports diagnostics. Exit codes: 0 valid;
/// 3 invalid root; 4 when `--fail-on-warning` trips.
@CommandDefinition(
    name = "validate",
    description = "Parse-check ADF JSON and report diagnostics",
    generateHelp = true)
public class ValidateCommand extends Adf4jSubcommand {

  @Option(
      shortName = 'f',
      name = "format",
      description = "text (default) or json {valid, diagnostics}",
      allowedValues = {"text", "json"})
  String format = "text";

  @Option(
      name = "fail-on-warning",
      hasValue = false,
      description = "Exit 4 when a WARNING is present (else 0 on a valid root)")
  boolean failOnWarning;

  @Override
  int run() {
    var parsed = parseInput();
    var hasWarning =
        parsed.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.severity() == Diagnostic.Severity.WARNING);

    String output;
    if (format.equals("json")) {
      output = new CliJson().write(JsonRenderer.parseResult(parsed), !common.compact) + "\n";
    } else {
      output = validateText(parsed);
    }
    CliIo.writeOutput(common.output, output);

    if (!parsed.isValid()) {
      return ExitCodes.CONTENT_FAILURE;
    }
    if (failOnWarning && hasWarning) {
      return ExitCodes.QUALITY_GATE;
    }
    return ExitCodes.OK;
  }

  private String validateText(ParseResult parsed) {
    var sb = new StringBuilder();
    sb.append("valid: ").append(parsed.isValid()).append('\n');
    if (parsed.diagnostics().isEmpty()) {
      sb.append("diagnostics: none\n");
      return sb.toString();
    }
    sb.append("diagnostics:\n");
    for (var diagnostic : parsed.diagnostics()) {
      sb.append("  ").append(describe(diagnostic)).append('\n');
    }
    return sb.toString();
  }
}
