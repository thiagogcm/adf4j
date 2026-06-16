package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.ParseResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * The CLI: dispatches to {@code convert} / {@code analyze} / {@code validate} and owns the
 * exit-code contract. Stdout carries only the deliverable; diagnostics and warnings go to stderr.
 */
final class Cli {

  private static final String UNKNOWN_VERSION = "unknown";
  private static final String VERSION_RESOURCE = "/dev/nthings/adf4j/cli/adf4j-cli.properties";
  private static final String VERSION_KEY = "version";

  static final String VERSION = resolveVersion();

  private final InputStream in;
  private final PrintStream out;
  private final PrintStream err;
  private final CliJson json = new CliJson();
  private boolean verbose;

  Cli(InputStream in, PrintStream out, PrintStream err) {
    this.in = in;
    this.out = out;
    this.err = err;
  }

  private static String resolveVersion() {
    var implementationVersion = Cli.class.getPackage().getImplementationVersion();
    return implementationVersion != null && !implementationVersion.isBlank()
        ? implementationVersion
        : loadVersionResource();
  }

  private static String loadVersionResource() {
    var stream = Cli.class.getResourceAsStream(VERSION_RESOURCE);
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

  private static String versionLine() {
    return "adf4j " + VERSION + "\n";
  }

  int run(String[] argv) {
    var args = List.of(argv);
    if (args.isEmpty() || args.getFirst().equals("-h") || args.getFirst().equals("--help")) {
      out.print(Help.GLOBAL);
      return ExitCodes.OK;
    }
    var command = args.getFirst();
    if (command.equals("-V") || command.equals("--version")) {
      out.print(versionLine());
      return ExitCodes.OK;
    }
    var rest = args.subList(1, args.size());
    verbose = rest.contains("-v") || rest.contains("--verbose");

    try {
      return switch (command) {
        case "convert" -> convert(rest);
        case "analyze" -> analyze(rest);
        case "validate" -> validate(rest);
        default ->
            throw CliException.usage(
                "unknown command '" + command + "'; expected convert, analyze, or validate");
      };
    } catch (CliException exception) {
      err.println("error: " + exception.getMessage());
      if (verbose && exception.getCause() != null) {
        exception.getCause().printStackTrace(err);
      }
      return exception.exitCode();
    } catch (RuntimeException exception) {
      err.println("internal error: " + exception);
      if (verbose) {
        exception.printStackTrace(err);
      }
      return ExitCodes.INTERNAL;
    }
  }

  private boolean printedHelpOrVersion(Args args, String help) {
    if (args.has("help")) {
      out.print(help);
      return true;
    }
    if (args.has("version")) {
      out.print(versionLine());
      return true;
    }
    return false;
  }

  // ---- convert ------------------------------------------------------------

  private int convert(List<String> tokens) {
    var spec =
        baseSpec()
            .value("format", "-f", "--format")
            .flag("compact", "--compact")
            .flag("fail-on-lossy", "--fail-on-lossy");
    addRenderingOptions(spec);
    var args = Args.parse(tokens, spec);
    if (printedHelpOrVersion(args, Help.CONVERT)) {
      return ExitCodes.OK;
    }
    var format = format(args, "md", Set.of("md", "json"));
    warnVerbatim(args);

    var input = CliIo.readInput(args.positionals(), in);
    var options = RenderConfig.build(args, json);
    var converter = AdfToMarkdown.with(options);

    MarkdownResult result;
    try {
      result = converter.convert(input);
    } catch (IllegalStateException fail) { // UnknownNodePolicy.FAIL aborts
      throw new CliException(
          ExitCodes.CONTENT_FAILURE,
          "conversion aborted by --unknown-nodes fail: " + fail.getMessage(),
          fail);
    }

    String output;
    if (format.equals("json")) {
      var node = new JsonRenderer(json, converter, options).markdownResult(result);
      output = json.write(node, !args.has("compact")) + "\n";
    } else {
      output = result.body();
    }
    CliIo.writeOutput(args.value("output"), output, out);

    printSummary(args, result.diagnostics(), result.wasLossy());
    if (args.has("fail-on-lossy") && result.wasLossy()) {
      return ExitCodes.QUALITY_GATE;
    }
    return ExitCodes.OK;
  }

  // ---- analyze ------------------------------------------------------------

  private int analyze(List<String> tokens) {
    var spec =
        baseSpec()
            .value("format", "-f", "--format")
            .flag("compact", "--compact")
            .value("select", "--select");
    addRenderingOptions(spec);
    var args = Args.parse(tokens, spec);
    if (printedHelpOrVersion(args, Help.ANALYZE)) {
      return ExitCodes.OK;
    }
    var format = format(args, "json", Set.of("json", "text"));
    warnVerbatim(args);
    var selected = selectedSections(args);

    var input = CliIo.readInput(args.positionals(), in);
    var options = RenderConfig.build(args, json);
    var converter = AdfToMarkdown.with(options);
    var metadata = converter.analyze(input);
    var renderer = new JsonRenderer(json, converter, options);

    String output;
    if (format.equals("text")) {
      output = analyzeText(metadata, selected, converter, options);
    } else {
      output = json.write(renderer.metadata(metadata, selected), !args.has("compact")) + "\n";
    }
    CliIo.writeOutput(args.value("output"), output, out);
    return ExitCodes.OK;
  }

  // ---- validate -----------------------------------------------------------

  private int validate(List<String> tokens) {
    var spec =
        baseSpec()
            .value("format", "-f", "--format")
            .flag("compact", "--compact")
            .flag("fail-on-warning", "--fail-on-warning");
    var args = Args.parse(tokens, spec);
    if (printedHelpOrVersion(args, Help.VALIDATE)) {
      return ExitCodes.OK;
    }
    var format = format(args, "text", Set.of("text", "json"));

    var input = CliIo.readInput(args.positionals(), in);
    var converter = AdfToMarkdown.create();
    var parsed = converter.parse(input);
    var hasError = false;
    var hasWarning = false;
    for (var issue : parsed.issues()) {
      hasError |= issue.severity() == Diagnostic.Severity.ERROR;
      hasWarning |= issue.severity() == Diagnostic.Severity.WARNING;
    }

    String output;
    if (format.equals("json")) {
      var renderer = new JsonRenderer(json, converter, MarkdownOptions.defaults());
      output = json.write(renderer.parseResult(parsed), !args.has("compact")) + "\n";
    } else {
      output = validateText(parsed);
    }
    CliIo.writeOutput(args.value("output"), output, out);

    if (!parsed.validAdfRoot() || hasError) {
      return ExitCodes.CONTENT_FAILURE;
    }
    if (args.has("fail-on-warning") && hasWarning) {
      return ExitCodes.QUALITY_GATE;
    }
    return ExitCodes.OK;
  }

  // ---- shared option specs ------------------------------------------------

  private static Args.Spec baseSpec() {
    return new Args.Spec()
        .flag("help", "-h", "--help")
        .flag("version", "-V", "--version")
        .flag("verbose", "-v", "--verbose")
        .flag("quiet", "-q", "--quiet")
        .value("output", "-o", "--output");
  }

  private static void addRenderingOptions(Args.Spec spec) {
    spec.value("title", "-t", "--title")
        .flag("collapse-hard-breaks", "-c", "--collapse-hard-breaks")
        .flag("escape-parentheses", "-p", "--escape-parentheses")
        .flag("image-size", "--image-size")
        .flag("html-visual-marks", "--html-visual-marks")
        .value("unknown-nodes", "--unknown-nodes")
        .value("table-fallback", "--table-fallback")
        .value("media-url", "--media-url")
        .value("media-map", "--media-map")
        .value("attachment-url", "--attachment-url")
        .value("attachment-map", "--attachment-map")
        .value("page-url", "--page-url")
        .value("page-map", "--page-map")
        .value("page-tree-map", "--page-tree-map")
        .value("excerpt-map", "--excerpt-map")
        .value("attachments-map", "--attachments-map")
        .value("extension-map", "--extension-map");
  }

  // ---- shared behavior ----------------------------------------------------

  private String format(Args args, String fallback, Set<String> allowed) {
    var value = args.value("format", fallback);
    if (!allowed.contains(value)) {
      throw CliException.usage("--format must be one of: " + String.join(", ", allowed));
    }
    return value;
  }

  private Set<String> selectedSections(Args args) {
    var raw = args.values("select");
    if (raw.isEmpty()) {
      return JsonRenderer.METADATA_KEY_SET;
    }
    var selected = new LinkedHashSet<String>();
    for (var token : raw) {
      for (var part : token.split(",")) {
        var section = part.strip();
        if (section.isEmpty()) {
          continue;
        }
        if (!JsonRenderer.METADATA_KEY_SET.contains(section)) {
          throw CliException.usage(
              "--select unknown section '"
                  + section
                  + "'; allowed: "
                  + String.join(", ", JsonRenderer.METADATA_KEYS));
        }
        selected.add(section);
      }
    }
    return selected;
  }

  private void warnVerbatim(Args args) {
    if (args.has("quiet")) {
      return;
    }
    for (var flag : List.of("excerpt-map", "extension-map")) {
      if (args.value(flag) != null) {
        err.println(
            "warning: --"
                + flag
                + " content is emitted verbatim and not HTML-sanitized;"
                + " use only trusted files");
      }
    }
  }

  private void printSummary(Args args, List<Diagnostic> diagnostics, boolean lossy) {
    if (args.has("quiet") || diagnostics.isEmpty()) {
      return;
    }
    var warnings = 0;
    var errors = 0;
    for (var diagnostic : diagnostics) {
      if (diagnostic.severity() == Diagnostic.Severity.WARNING) {
        warnings++;
      } else if (diagnostic.severity() == Diagnostic.Severity.ERROR) {
        errors++;
      }
    }
    err.println("adf4j: " + warnings + " warning(s), " + errors + " error(s), lossy=" + lossy);
  }

  private String analyzeText(
      ContentMetadata metadata,
      Set<String> selected,
      AdfToMarkdown converter,
      MarkdownOptions options) {
    var sb = new StringBuilder();
    if (selected.contains("outline")) {
      sb.append("outline:\n");
      for (var heading : metadata.outline()) {
        sb.append("  ")
            .append("  ".repeat(Math.max(0, heading.level() - 1)))
            .append("H")
            .append(heading.level())
            .append(' ')
            .append(heading.text())
            .append(" (#")
            .append(heading.anchor())
            .append(")\n");
      }
    }
    if (selected.contains("pageRefs")) {
      appendList(sb, "pageRefs", metadata.pageRefs().stream().map(p -> p.pageNodeId()).toList());
    }
    if (selected.contains("externalRefs")) {
      appendList(sb, "externalRefs", metadata.externalRefs().stream().map(e -> e.url()).toList());
    }
    if (selected.contains("mentionRefs")) {
      appendList(
          sb,
          "mentionRefs",
          metadata.mentionRefs().stream().map(m -> m.text() + " [" + m.id() + "]").toList());
    }
    if (selected.contains("attachmentRefs")) {
      appendList(
          sb,
          "attachmentRefs",
          metadata.attachmentRefs().stream()
              .map(a -> a.title() + " [" + a.fileId() + "]")
              .toList());
    }
    if (selected.contains("referencedFileIds")) {
      appendList(sb, "referencedFileIds", List.copyOf(metadata.referencedFileIds()));
    }
    if (selected.contains("pageTreeRefs")) {
      appendList(
          sb,
          "pageTreeRefs",
          metadata.pageTreeRefs().stream()
              .map(r -> r.macro().name().toLowerCase(Locale.ROOT) + " root=" + r.root())
              .toList());
    }
    if (selected.contains("excerptRefs")) {
      appendList(
          sb,
          "excerptRefs",
          metadata.excerptRefs().stream()
              .map(r -> r.page() + (r.excerptName() == null ? "" : "/" + r.excerptName()))
              .toList());
    }
    if (selected.contains("excerpts")) {
      appendList(
          sb,
          "excerpts",
          metadata.excerpts().stream()
              .map(
                  e ->
                      (e.name() == null ? "(unnamed)" : e.name())
                          + ": "
                          + converter
                              .convert(new AdfDocument(1, e.content()), options)
                              .body()
                              .strip())
              .toList());
    }
    return sb.toString();
  }

  private static void appendList(StringBuilder sb, String label, List<String> values) {
    sb.append(label).append(" (").append(values.size()).append("):");
    if (values.isEmpty()) {
      sb.append(" none\n");
      return;
    }
    sb.append('\n');
    for (var value : values) {
      sb.append("  ").append(value).append('\n');
    }
  }

  private String validateText(ParseResult parsed) {
    var sb = new StringBuilder();
    sb.append("valid: ").append(parsed.validAdfRoot()).append('\n');
    if (parsed.issues().isEmpty()) {
      sb.append("issues: none\n");
      return sb.toString();
    }
    sb.append("issues:\n");
    for (var issue : parsed.issues()) {
      sb.append("  [")
          .append(issue.severity())
          .append("] ")
          .append(issue.code())
          .append(": ")
          .append(issue.message())
          .append('\n');
    }
    return sb.toString();
  }
}
