package dev.nthings.adf4j.internal;

import java.util.Set;

/// URL scheme safety independent of an output format. The allowed schemes are the caller's
/// allow-list, not a universal HTML policy; each output backend selects the schemes it can emit.
public final class UrlSafety {

  private UrlSafety() {}

  /// Percent-encodes the colon of a URL whose scheme is not in `allowedSchemes`; allowed and
  /// relative URLs pass through unchanged. A defused URL also drops C0 controls and DEL, which
  /// browsers ignore and which could otherwise hide a scheme.
  public static String sanitizeScheme(String url, Set<String> allowedSchemes) {
    if (hasSafeOrNoScheme(url, allowedSchemes)) {
      return url;
    }
    var cleaned = new StringBuilder(url.length());
    for (var i = 0; i < url.length(); i++) {
      var c = url.charAt(i);
      if (c >= ' ' && c != 0x7f) {
        cleaned.append(c);
      }
    }
    var colon = cleaned.indexOf(":");
    if (colon >= 0) {
      cleaned.replace(colon, colon + 1, "%3A");
    }
    return cleaned.toString();
  }

  /// A CommonMark absolute URI: an ASCII scheme of 2 to 32 characters, then `:`.
  public static boolean isAbsoluteUri(String url) {
    var colon = url.indexOf(':');
    if (colon < 2 || colon > 32) {
      return false;
    }
    for (var i = 0; i < colon; i++) {
      if (!isSchemeChar(url.charAt(i), i == 0)) {
        return false;
      }
    }
    return true;
  }

  // Relative, or an allowed scheme. A tab/CR/LF inside the scheme is unsafe: browsers strip it
  // before re-reading the scheme.
  private static boolean hasSafeOrNoScheme(String url, Set<String> allowedSchemes) {
    var i = 0;
    while (i < url.length() && url.charAt(i) <= ' ') {
      i++;
    }
    var start = i;
    var dirty = false;
    for (; i < url.length(); i++) {
      var c = url.charAt(i);
      if (c == ':') {
        return !dirty && i > start && isAllowedScheme(url, start, i, allowedSchemes);
      }
      if (c == '\t' || c == '\n' || c == '\r') {
        dirty = true;
      } else if (!isSchemeChar(c, i == start)) {
        return true; // no scheme before ':'
      }
    }
    return true;
  }

  private static boolean isAllowedScheme(
      String url, int start, int end, Set<String> allowedSchemes) {
    for (var scheme : allowedSchemes) {
      if (scheme.length() == end - start
          && url.regionMatches(true, start, scheme, 0, scheme.length())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSchemeChar(char c, boolean first) {
    var letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    return letter || (!first && ((c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.'));
  }
}
