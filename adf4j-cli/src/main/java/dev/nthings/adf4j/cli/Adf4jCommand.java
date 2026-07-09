package dev.nthings.adf4j.cli;

import java.io.IOException;
import java.util.Properties;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

/// The root `adf4j` group command. Subcommands do the work; invoking the root prints the
/// version when asked and the generated help otherwise.
@CommandDefinition(
    name = "adf4j",
    description = "Atlassian Document Format (ADF) processing. Input is read from FILE or stdin.",
    generateHelp = true,
    groupCommands = {ConvertCommand.class, AnalyzeCommand.class, ValidateCommand.class})
public class Adf4jCommand implements Command<CommandInvocation> {

  private static final String UNKNOWN_VERSION = "unknown";
  private static final String VERSION_RESOURCE = "/dev/nthings/adf4j/cli/adf4j-cli.properties";
  private static final String VERSION_KEY = "version";

  static final String VERSION = resolveVersion();

  @Option(
      shortName = 'V',
      name = "version",
      hasValue = false,
      description = "Print the version and exit")
  boolean version;

  @Override
  public CommandResult execute(CommandInvocation invocation) {
    if (version) {
      invocation.print("adf4j " + VERSION + "\n");
    } else {
      invocation.println(invocation.getHelpInfo());
    }
    return CommandResult.SUCCESS;
  }

  private static String resolveVersion() {
    var implementationVersion = Adf4jCommand.class.getPackage().getImplementationVersion();
    return implementationVersion != null && !implementationVersion.isBlank()
        ? implementationVersion
        : loadVersionResource();
  }

  private static String loadVersionResource() {
    var stream = Adf4jCommand.class.getResourceAsStream(VERSION_RESOURCE);
    if (stream == null) {
      return UNKNOWN_VERSION;
    }
    try (stream) {
      var properties = new Properties();
      properties.load(stream);
      return properties.getProperty(VERSION_KEY, UNKNOWN_VERSION);
    } catch (IOException failure) {
      return UNKNOWN_VERSION;
    }
  }
}
