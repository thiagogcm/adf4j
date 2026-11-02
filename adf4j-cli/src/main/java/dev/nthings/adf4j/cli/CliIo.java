package dev.nthings.adf4j.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// The CLI's one read path, for the ADF input and every map file, and its output path. A file
/// output goes through a temp file + atomic rename so a crash mid-write can't leave a truncated
/// file.
final class CliIo {

  private CliIo() {}

  /// Reads `path`, or stdin when it is `null`, as strict UTF-8. `label` names the source in errors.
  /// A missing, unreadable or undecodable source is an I/O error (exit 1).
  static String read(@Nullable Path path, String label) {
    try {
      var bytes = path == null ? System.in.readAllBytes() : Files.readAllBytes(path);
      return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    } catch (NoSuchFileException exception) {
      throw CliException.io(label + " not found: " + path, exception);
    } catch (IOException exception) {
      var source = path == null ? "stdin" : label + " '" + path + "'";
      throw CliException.io("failed to read " + source + ": " + exception.getMessage(), exception);
    }
  }

  static void writeOutput(@Nullable String outputPath, String content) {
    if (outputPath == null) {
      System.out.print(content);
      System.out.flush();
      if (System.out.checkError()) {
        throw new CliException(ExitCodes.IO, "failed to write stdout");
      }
      return;
    }
    if (outputPath.isBlank()) {
      throw CliException.usage("--output path must not be empty");
    }
    var requested = Path.of(outputPath);
    Path temp = null;
    try {
      // Renaming over a symlink would replace the link, so write through to the file it names.
      var existing = Files.exists(requested);
      var target = existing ? requested.toRealPath() : requested;
      temp = createTemp(target, existing);
      Files.writeString(temp, content, StandardCharsets.UTF_8);
      // Only now: a read-only target's permissions would have refused the write.
      if (existing && target.getFileSystem().supportedFileAttributeViews().contains("posix")) {
        Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(target));
      }
      try {
        Files.move(
            temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException exception) {
      throw CliException.io(
          "failed to write output file '" + requested + "': " + exception.getMessage(), exception);
    } finally {
      deleteQuietly(temp);
    }
  }

  // Replacing a file keeps createTempFile's private 0600 until the content is complete. A new file
  // has no permissions to keep, and createFile leaves its mode to the umask, as for any new file.
  private static Path createTemp(Path target, boolean existing) throws IOException {
    var absolute = target.toAbsolutePath();
    return existing
        ? Files.createTempFile(absolute.getParent(), ".adf4j-", ".tmp")
        : Files.createFile(absolute.resolveSibling(".adf4j-" + UUID.randomUUID() + ".tmp"));
  }

  private static void deleteQuietly(@Nullable Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // best-effort temp cleanup; the rename already succeeded or the write already failed
    }
  }
}
