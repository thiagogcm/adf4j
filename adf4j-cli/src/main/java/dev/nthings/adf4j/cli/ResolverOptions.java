package dev.nthings.adf4j.cli;

import org.aesh.command.option.Option;
import org.jspecify.annotations.Nullable;

/// Resolver options of `convert` (URL templates and JSON lookup tables), via `@Mixin`.
/// {@link RenderConfig} turns them into library resolvers.
final class ResolverOptions {

  @Option(
      name = "media-url",
      description = "File-media URL template; placeholders {id} {collection} {localId}")
  @Nullable String mediaUrl;

  @Option(
      name = "media-map",
      description = "JSON object file: file id -> URL (wins over the template)")
  @Nullable String mediaMap;

  @Option(
      name = "attachment-url",
      description = "Attachment URL template; placeholders {fileId} {title}")
  @Nullable String attachmentUrl;

  @Option(name = "attachment-map", description = "JSON object file: file id -> URL")
  @Nullable String attachmentMap;

  @Option(name = "page-url", description = "Inter-page link template; placeholder {pageId}")
  @Nullable String pageUrl;

  @Option(name = "page-map", description = "JSON object file: page node id -> URL")
  @Nullable String pageMap;

  @Option(name = "page-tree-map", description = "pagetree/children expansion table file")
  @Nullable String pageTreeMap;

  @Option(
      name = "excerpt-map",
      description = "excerpt-include content table file (emitted verbatim)")
  @Nullable String excerptMap;

  @Option(
      name = "extension-map",
      description = "Custom-extension templates file by key (emitted verbatim)")
  @Nullable String extensionMap;

  /// Warns on stderr that verbatim-emitted files are not HTML-sanitized, unless `--quiet`.
  void warnVerbatim(boolean quiet) {
    if (quiet) {
      return;
    }
    warnVerbatim("excerpt-map", excerptMap);
    warnVerbatim("extension-map", extensionMap);
  }

  private static void warnVerbatim(String flag, @Nullable String value) {
    if (value != null) {
      System.err.println(
          "warning: --"
              + flag
              + " content is emitted verbatim and not HTML-sanitized;"
              + " use only trusted files");
    }
  }
}
