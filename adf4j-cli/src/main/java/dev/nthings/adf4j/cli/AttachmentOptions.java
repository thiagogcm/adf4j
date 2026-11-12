package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.result.AdfException;
import java.nio.file.Path;
import java.util.List;
import org.aesh.command.option.Option;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.JsonNodeFactory;

/// Attachment inventory option shared by `convert` and `analyze`, via `@Mixin`. The file holds the
/// `context.attachments` array of the portable configuration, so the library reads both.
final class AttachmentOptions {

  private static final String FLAG = "--attachments";

  @Option(name = "attachments", description = "Confluence attachment inventory file for this page")
  @Nullable String path;

  /// Inventory entries, or `null` without the flag. An empty inventory is still authoritative.
  @Nullable List<AttachmentReference> read(CliJson json) {
    if (path == null) {
      return null;
    }
    var config = JsonNodeFactory.instance.objectNode();
    config.putObject("context").set("attachments", json.readFile(Path.of(path), FLAG));
    try {
      return ConversionOptions.fromJson(json.write(config, false)).attachments();
    } catch (AdfException invalid) {
      throw CliException.usage(FLAG + ": " + invalid.getMessage());
    }
  }
}
