package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfValidation;
import dev.nthings.adf4j.result.OperationResult;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;

/// `adf4j validate`: parse-checks ADF JSON and reports diagnostics. Exit codes: 0 valid;
/// 3 invalid root; 4 when `--fail-on-warning` trips.
@CommandDefinition(
    name = "validate",
    description = "Validate ADF JSON against the bundled schema",
    generateHelp = true)
public class ValidateCommand extends Adf4jSubcommand {

  @Option(
      shortName = 'f',
      name = "format",
      description = "text (default) or structured JSON",
      allowedValues = {"text", "json"})
  String format = "text";

  @Option(
      name = "fail-on-warning",
      hasValue = false,
      description = "Exit 4 when a WARNING is present (else 0 on a valid root)")
  boolean failOnWarning;

  @Option(
      name = "parse-only",
      hasValue = false,
      description = "Check parseability without schema validation")
  boolean parseOnly;

  @Override
  int run() {
    var input = readInput();
    OperationResult parsed = parseOnly ? AdfJson.parse(input) : AdfValidation.validate(input);

    String output;
    if (format.equals("json")) {
      output = AdfJson.writeResult(parsed, !common.compact) + "\n";
    } else {
      output = validateText(parsed);
    }
    CliIo.writeOutput(common.output, output);

    if (!parsed.isSuccess()) {
      return ExitCodes.CONTENT_FAILURE;
    }
    if (failOnWarning && parsed.hasWarnings()) {
      return ExitCodes.QUALITY_GATE;
    }
    return ExitCodes.OK;
  }

  private String validateText(OperationResult parsed) {
    var sb = new StringBuilder();
    sb.append("valid: ").append(parsed.isSuccess()).append('\n');
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
