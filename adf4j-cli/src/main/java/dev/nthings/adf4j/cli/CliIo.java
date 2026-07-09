package dev.nthings.adf4j.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.jspecify.annotations.Nullable;

/// Reads the ADF input (a file argument or stdin) and writes the deliverable; a file output goes
/// through a temp file + atomic rename so a crash mid-write can't leave a truncated file.
final class CliIo {

  private CliIo() {}

  static String readInput(@Nullable String file) {
    if (file == null || file.equals("-")) {
      try {
        return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException exception) {
        throw CliException.io("failed to read stdin: " + exception.getMessage(), exception);
      }
    }
    var path = Path.of(file);
    if (!Files.exists(path)) {
      throw new CliException(ExitCodes.IO, "input file not found: " + path);
    }
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw CliException.io(
          "failed to read input file '" + path + "': " + exception.getMessage(), exception);
    }
  }

  static void writeOutput(@Nullable String outputPath, String content) {
    if (outputPath == null) {
      System.out.print(content);
      System.out.flush();
      return;
    }
    if (outputPath.isBlank()) {
      throw CliException.usage("--output path must not be empty");
    }
    var target = Path.of(outputPath);
    var directory = target.toAbsolutePath().getParent();
    Path temp = null;
    try {
      temp = Files.createTempFile(directory, ".adf4j-", ".tmp");
      Files.writeString(temp, content, StandardCharsets.UTF_8);
      try {
        Files.move(
            temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException exception) {
      throw CliException.io(
          "failed to write output file '" + target + "': " + exception.getMessage(), exception);
    } finally {
      deleteQuietly(temp);
    }
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
