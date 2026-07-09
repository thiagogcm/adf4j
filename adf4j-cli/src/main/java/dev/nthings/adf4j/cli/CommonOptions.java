package dev.nthings.adf4j.cli;

import org.aesh.command.option.Option;
import org.jspecify.annotations.Nullable;

/// Options shared by every subcommand, composed into commands via `@Mixin`. Fields are
/// package-private so the aesh annotation processor can generate reflection-free accessors.
final class CommonOptions {

  @Option(
      shortName = 'o',
      name = "output",
      description = "Write to FILE (atomically) instead of stdout")
  @Nullable String output;

  @Option(name = "compact", hasValue = false, description = "Single-line JSON instead of pretty")
  boolean compact;

  @Option(
      shortName = 'q',
      name = "quiet",
      hasValue = false,
      description = "Suppress the stderr diagnostics summary and warnings")
  boolean quiet;

  @Option(
      shortName = 'v',
      name = "verbose",
      hasValue = false,
      description = "Show stack traces on error")
  boolean verbose;
}
