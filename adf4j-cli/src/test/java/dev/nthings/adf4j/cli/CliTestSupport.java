package dev.nthings.adf4j.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/// Drives {@link Main#run} with swapped system streams so tests can assert exit code, stdout, and
/// stderr. The aesh runtime and the commands both resolve `System.in/out/err` at call time, so a
/// swap around the run captures everything.
final class CliTestSupport {

  /// The captured outcome of one CLI invocation.
  record Result(int exitCode, String out, String err) {}

  static final String SIMPLE_DOC =
      """
      {"version":1,"type":"doc","content":[
        {"type":"heading","attrs":{"level":1},"content":[{"type":"text","text":"Hello"}]},
        {"type":"paragraph","content":[{"type":"text","text":"World"}]}
      ]}""";

  // Drops an unknown mark -> a WARNING diagnostic -> wasLossy() is true.
  static final String LOSSY_DOC =
      """
      {"version":1,"type":"doc","content":[
        {"type":"paragraph","content":[{"type":"text","text":"hi","marks":[{"type":"bogusMark"}]}]}
      ]}""";

  // An unknown block node -> aborts under --unknown-nodes fail.
  static final String UNKNOWN_NODE_DOC =
      """
      {"version":1,"type":"doc","content":[{"type":"bogusBlock"}]}""";

  static final String MEDIA_DOC =
      """
      {"version":1,"type":"doc","content":[
        {"type":"mediaSingle","content":[
          {"type":"media","attrs":{"type":"file","id":"FILE_ID","collection":"col"}}]}
      ]}""";

  static synchronized Result run(String stdin, String... args) {
    var originalIn = System.in;
    var originalOut = System.out;
    var originalErr = System.err;
    var out = new ByteArrayOutputStream();
    var err = new ByteArrayOutputStream();
    int exit;
    try {
      System.setIn(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      exit = Main.run(args);
    } finally {
      System.setIn(originalIn);
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
    return new Result(
        exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  /// Runs the `convert` subcommand with the given stdin and extra args.
  static Result convert(String stdin, String... args) {
    var full = new String[args.length + 1];
    full[0] = "convert";
    System.arraycopy(args, 0, full, 1, args.length);
    return run(stdin, full);
  }

  /// Runs with empty stdin; for invocations (help/version) that don't read input.
  static Result runNoInput(String... args) {
    return run("", args);
  }

  static Path write(Path dir, String name, String content) {
    try {
      var path = dir.resolve(name);
      Files.writeString(path, content);
      return path;
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  private CliTestSupport() {}
}
