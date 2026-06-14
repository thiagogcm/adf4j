package dev.nthings.adf4j.cli;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A resolver URL template such as {@code https://cdn/{collection}/{id}}. Placeholder names are
 * validated against {@code allowed} at construction (an unknown one is a usage error before any input
 * is read). Only the substituted value is percent-encoded (literal separators are preserved), and the
 * result is still scheme-sanitized by the library.
 */
final class UrlTemplate {

  static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]*)}");
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private final String template;

  UrlTemplate(String template, Set<String> allowed, String flag) {
    this.template = template;
    var matcher = PLACEHOLDER.matcher(template);
    while (matcher.find()) {
      if (!allowed.contains(matcher.group(1))) {
        throw CliException.usage(
            "unknown placeholder '{" + matcher.group(1) + "}' in " + flag + "; allowed: " + allowed);
      }
    }
  }

  /** Expands the template, percent-encoding each substituted value; absent values become empty. */
  String expand(Map<String, String> values) {
    return render(template, name -> encode(Objects.requireNonNullElse(values.get(name), "")));
  }

  /** Substitutes {@code {name}} tokens via {@code lookup}; shared by URL and extension templates. */
  static String render(String template, UnaryOperator<String> lookup) {
    var matcher = PLACEHOLDER.matcher(template);
    var out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(out, Matcher.quoteReplacement(lookup.apply(matcher.group(1))));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  // Encode everything outside the RFC 3986 unreserved set (uppercase hex), so traversal/query/fragment
  // metacharacters in an untrusted value can't change the URL's structure.
  private static String encode(String value) {
    var out = new StringBuilder(value.length());
    for (var i = 0; i < value.length(); i++) {
      var ch = value.charAt(i);
      if (isUnreserved(ch)) {
        out.append(ch);
      } else {
        for (var b : String.valueOf(ch).getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
          out.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        }
      }
    }
    return out.toString();
  }

  private static boolean isUnreserved(char ch) {
    return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
        || ch == '-' || ch == '_' || ch == '.' || ch == '~';
  }
}
