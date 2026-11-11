package dev.nthings.adf4j.testing;

import dev.nthings.adf4j.result.MarkdownResult;
import java.util.ArrayList;
import java.util.List;

/// Everything a conversion reports, as one readable block, so a test can pin the whole outcome:
/// an unexpected extra diagnostic, effect or unresolved dependency fails it. Messages are left to
/// the goldens; this pins codes, severities, kinds and paths.
///
/// ```
/// <body>
/// == diagnostics
/// WARNING UNKNOWN_NODE_PLACEHOLDER [/content/0]
/// == effects
/// PLACEHOLDER /content/0
/// == unresolved
/// PAGE_TREE NO_ANSWER /content/0 id= fallback={{pagetree}}
/// ```
public final class Outcome {

  private Outcome() {}

  public static String of(MarkdownResult result) {
    var lines = new ArrayList<String>();
    lines.add(result.body());
    section(
        lines,
        "diagnostics",
        result.diagnostics().stream()
            .map(d -> d.severity() + " " + d.code() + " " + d.paths())
            .toList());
    section(
        lines, "effects", result.effects().stream().map(e -> e.kind() + " " + e.path()).toList());
    section(
        lines,
        "unresolved",
        result.unresolved().stream()
            .map(
                r ->
                    r.kind()
                        + " "
                        + r.reason()
                        + " "
                        + r.path()
                        + " id="
                        + r.id()
                        + " fallback="
                        + r.fallback())
            .toList());
    return String.join("\n", lines);
  }

  private static void section(List<String> lines, String name, List<String> entries) {
    if (entries.isEmpty()) return;
    lines.add("== " + name);
    lines.addAll(entries);
  }
}
