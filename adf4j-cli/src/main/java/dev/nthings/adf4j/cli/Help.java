package dev.nthings.adf4j.cli;

/** Usage text for the CLI and its subcommands. */
final class Help {

  static final String GLOBAL = """
      adf4j - Atlassian Document Format (ADF) processing

      Usage: adf4j <command> [options] [<input-file>]

      Commands:
        convert    Convert ADF JSON to Markdown
        analyze    Extract references, attachments, and outline as JSON
        validate   Parse-check ADF JSON and report diagnostics

      Input is read from <input-file> or stdin. Run 'adf4j <command> --help' for details.

      Global options (after the command):
        -h, --help       Show this help
        -V, --version    Show the version
        -v, --verbose    Show stack traces on error
        -q, --quiet      Suppress the stderr diagnostics summary and warnings

      Examples:
        adf4j convert doc.adf.json
        cat doc.adf.json | adf4j convert -t "My Page" -o out.md
        adf4j analyze --select referencedFileIds,outline doc.adf.json
        adf4j validate --fail-on-warning doc.adf.json
      """;

  static final String CONVERT = """
      adf4j convert - Convert ADF JSON to Markdown

      Usage: adf4j convert [options] [<input-file>]

      Output:
        -o, --output FILE          Write to FILE (atomically) instead of stdout
        -f, --format md|json       md (default) prints the body; json prints the full result
                                   {body, wasLossy, diagnostics, metadata, unresolved}
            --compact              Single-line JSON instead of pretty
            --fail-on-lossy        Exit 4 when the result is lossy (any WARNING/ERROR diagnostic)
        -q, --quiet                Suppress the stderr diagnostics summary

      Rendering:
        -t, --title TITLE          Prepend TITLE as a level-1 (#) heading
        -c, --collapse-hard-breaks Render hard breaks as soft breaks
        -p, --escape-parentheses   Backslash-escape literal ( and )
            --image-size           Emit non-GFM image {width= height=} attributes
            --html-visual-marks    Keep visual-only marks as inline <span style>
            --unknown-nodes V      placeholder (default) | skip | fail | preserve-raw
            --table-fallback V     gfm-promote-first-row (default) | gfm-empty-header | html

      Resolvers (data-driven; see docs/usage-guide.md for file schemas):
            --media-url TEMPLATE   File-media URL template; placeholders {id} {collection} {localId}
            --media-map FILE       JSON object: file id -> URL (wins over the template)
            --attachment-url TPL   Attachment URL template; placeholders {fileId} {title}
            --attachment-map FILE  JSON object: file id -> URL
            --page-url TEMPLATE    Inter-page link template; placeholder {pageId}
            --page-map FILE        JSON object: page node id -> URL
            --page-tree-map FILE   pagetree/children expansion table
            --excerpt-map FILE     excerpt-include content table (emitted verbatim)
            --attachments-map FILE Confluence attachment inventory for this page
            --extension-map FILE   Custom-extension templates by key (emitted verbatim)

      Examples:
        adf4j convert -t "My Page" doc.adf.json > out.md
        adf4j convert -f json --fail-on-lossy doc.adf.json
        adf4j convert --page-url 'https://wiki/pages/{pageId}' doc.adf.json
      """;

  static final String ANALYZE = """
      adf4j analyze - Extract references, attachments, and outline

      Usage: adf4j analyze [options] [<input-file>]

        -o, --output FILE      Write to FILE instead of stdout
        -f, --format json|text json (default) | human-readable text
            --compact          Single-line JSON instead of pretty
            --select SECTIONS  Comma-separated subset of sections to emit. One or more of:
                               pageRefs, externalRefs, mentionRefs, attachmentRefs,
                               referencedFileIds, pageTreeRefs, excerptRefs, excerpts, outline
            --attachments-map FILE  Attachment inventory; without it, macro-based attachment
                                    file ids are absent from referencedFileIds

      Rendering/resolver options from 'convert' are also accepted (they affect excerpt rendering).

      Examples:
        adf4j analyze doc.adf.json
        adf4j analyze --select referencedFileIds doc.adf.json
        adf4j analyze -f text --select outline doc.adf.json
      """;

  static final String VALIDATE = """
      adf4j validate - Parse-check ADF JSON and report diagnostics

      Usage: adf4j validate [options] [<input-file>]

        -o, --output FILE      Write to FILE instead of stdout
        -f, --format text|json text (default) | json {validAdfRoot, issues}
            --compact          Single-line JSON instead of pretty
            --fail-on-warning  Exit 4 when a WARNING is present (else 0 on a valid root)

      Exit codes: 0 valid; 3 invalid root or an ERROR diagnostic; 4 --fail-on-warning tripped.

      Examples:
        adf4j validate doc.adf.json
        adf4j validate -f json doc.adf.json
      """;

  private Help() {}
}
