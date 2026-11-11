package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.metadata.AttachmentReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.aesh.command.option.Option;
import org.jspecify.annotations.Nullable;

/// Attachment inventory option shared by `convert` and `analyze`, via `@Mixin`.
final class AttachmentOptions {

  private static final String FLAG = "--attachments";
  private static final Set<String> KEYS = Set.of("fileId", "title", "mediaType", "downloadUrl");

  @Option(name = "attachments", description = "Confluence attachment inventory file for this page")
  @Nullable String path;

  /// Inventory entries, or `null` without the flag. An empty inventory is still authoritative.
  @Nullable List<AttachmentReference> read(CliJson json) {
    if (path == null) {
      return null;
    }
    var context = FLAG + " entry";
    var references = new ArrayList<AttachmentReference>();
    for (var entry : json.readEntries(path, FLAG, KEYS)) {
      references.add(
          new AttachmentReference(
              CliJson.requireString(entry, "fileId", context),
              CliJson.string(entry, "title", context),
              CliJson.string(entry, "mediaType", context),
              CliJson.string(entry, "downloadUrl", context)));
    }
    return references;
  }
}
