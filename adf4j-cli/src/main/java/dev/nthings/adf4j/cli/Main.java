package dev.nthings.adf4j.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.nthings.adf4j.AdfProcessor;
import dev.nthings.adf4j.OutputFormat;
import dev.nthings.adf4j.RenderOptions;

import org.jline.builtins.Options;

public final class Main {

    private static final String[] USAGE = {
        "adf4j-cli - Convert ADF JSON documents to different output formats",
        "",
        "Usage: adf4j-cli [-f <format>] [-o <file>] [<input-file>]",
        "",
        "  If no input file is provided, reads from stdin.",
        "",
        "Options:",
        "  -f, --format=FORMAT    Output format: storage-markdown, presentation-markdown, presentation-html",
        "                         (default: storage-markdown)",
        "  -o, --output=FILE      Write output to FILE instead of stdout",
        "  -h, --help             Show this help message",
    };

    public static void main(String[] args) throws Exception {
        int exitCode = run(args, System.in);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, InputStream stdinStream) throws Exception {
        Options options;
        try {
            options = Options.compile(USAGE).parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }

        if (options.isSet("help")) {
            options.usage(System.err);
            return 0;
        }

        OutputFormat outputFormat = parseFormat(options.get("format"));
        if (outputFormat == null) {
            System.err.println("Error: Invalid format. Valid formats: storage-markdown, presentation-markdown, presentation-html");
            return 1;
        }

        String input = readInput(options.args(), stdinStream);
        if (input == null) {
            return 1;
        }

        var processor = new AdfProcessor();
        String result = switch (outputFormat) {
            case STORAGE_MARKDOWN -> processor.renderStorageMarkdown(input, RenderOptions.defaults());
            case PRESENTATION_MARKDOWN -> processor.renderPresentationMarkdown(input, RenderOptions.defaults());
            case PRESENTATION_HTML -> processor.renderPresentationHtml(input, RenderOptions.defaults());
        };

        if (options.isSet("output")) {
            Files.writeString(Path.of(options.get("output")), result, StandardCharsets.UTF_8);
        } else {
            System.out.print(result);
        }

        return 0;
    }

    private static OutputFormat parseFormat(String format) {
        if (format == null || format.isEmpty()) {
            return OutputFormat.STORAGE_MARKDOWN;
        }
        return switch (format.toLowerCase()) {
            case "storage-markdown", "storage_markdown" -> OutputFormat.STORAGE_MARKDOWN;
            case "presentation-markdown", "presentation_markdown" -> OutputFormat.PRESENTATION_MARKDOWN;
            case "presentation-html", "presentation_html" -> OutputFormat.PRESENTATION_HTML;
            default -> null;
        };
    }

    private static String readInput(java.util.List<String> positionalArgs, InputStream stdinStream) throws IOException {
        if (!positionalArgs.isEmpty()) {
            Path inputPath = Path.of(positionalArgs.getFirst());
            if (!Files.exists(inputPath)) {
                System.err.println("Error: Input file not found: " + inputPath);
                return null;
            }
            return Files.readString(inputPath, StandardCharsets.UTF_8);
        }
        return new String(stdinStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private Main() {}
}
