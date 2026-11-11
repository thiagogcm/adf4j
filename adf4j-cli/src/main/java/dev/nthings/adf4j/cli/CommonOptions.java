package dev.nthings.adf4j.cli;

import org.aesh.command.option.Option;
import org.jspecify.annotations.Nullable;

/// Options shared by every subcommand, via `@Mixin`. Package-private fields let the aesh
/// annotation processor generate reflection-free accessors.
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
      description = "Suppress stderr diagnostics and warnings")
  boolean quiet;

  @Option(
      shortName = 'v',
      name = "verbose",
      hasValue = false,
      description = "Show library logs, and stack traces on error")
  boolean verbose;
}
