package dev.nthings.adf4j.cli;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The `{name}` tokens of a template, shared by the URL templates, which encode what they
/// substitute, and the `--extension-map` templates, which emit it verbatim.
final class Placeholders {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]*)}");

  private Placeholders() {}

  /// The token names in `template`, in order of appearance.
  static List<String> names(String template) {
    return PLACEHOLDER.matcher(template).results().map(token -> token.group(1)).toList();
  }

  /// Replaces each token with `lookup` of its name.
  static String render(String template, UnaryOperator<String> lookup) {
    return PLACEHOLDER
        .matcher(template)
        .replaceAll((MatchResult token) -> Matcher.quoteReplacement(lookup.apply(token.group(1))));
  }
}
