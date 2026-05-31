package dev.nthings.adf4j.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.nthings.adf4j.Adf;

import org.jline.builtins.Options;

public final class Main {

    private static final String[] USAGE = {
        "adf4j-cli - Convert ADF JSON documents to Markdown",
        "",
        "Usage: adf4j-cli [-o <file>] [<input-file>]",
        "",
        "  If no input file is provided, reads from stdin.",
        "",
        "Options:",
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

        String input = readInput(options.args(), stdinStream);
        if (input == null) {
            return 1;
        }

        String result = Adf.toMarkdown(input);

        if (options.isSet("output")) {
            Files.writeString(Path.of(options.get("output")), result, StandardCharsets.UTF_8);
        } else {
            System.out.print(result);
        }

        return 0;
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
