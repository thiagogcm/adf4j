package dev.nthings.adf4j.cli;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// A resolver URL template, such as `https://cdn/{collection}/{id}`. Placeholder names are checked
/// against `allowed` at construction, before any input is read. Only substituted values are
/// percent-encoded; the library still scheme-sanitizes the result.
final class UrlTemplate {

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private final String template;

  UrlTemplate(String template, Set<String> allowed, String flag) {
    this.template = template;
    for (var name : Placeholders.names(template)) {
      if (!allowed.contains(name)) {
        throw CliException.usage(
            "unknown placeholder '{" + name + "}' in " + flag + "; allowed: " + allowed);
      }
    }
  }

  /// Expands the template, percent-encoding each substituted value; an absent key becomes empty.
  String expand(Map<String, String> values) {
    return Placeholders.render(
        template, name -> encode(Objects.requireNonNullElse(values.get(name), "")));
  }

  // Encode all bytes outside the RFC 3986 unreserved set (uppercase hex), so metacharacters in an
  // untrusted value can't alter the URL. Encoding UTF-8 bytes keeps surrogate pairs whole.
  private static String encode(String value) {
    var out = new StringBuilder(value.length());
    for (var b : value.getBytes(StandardCharsets.UTF_8)) {
      if (isUnreserved((char) (b & 0xFF))) {
        out.append((char) b);
      } else {
        out.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
      }
    }
    return out.toString();
  }

  private static boolean isUnreserved(char ch) {
    return (ch >= 'A' && ch <= 'Z')
        || (ch >= 'a' && ch <= 'z')
        || (ch >= '0' && ch <= '9')
        || ch == '-'
        || ch == '_'
        || ch == '.'
        || ch == '~';
  }
}
