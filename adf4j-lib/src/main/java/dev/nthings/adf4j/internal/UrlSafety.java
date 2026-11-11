package dev.nthings.adf4j.internal;

import java.util.Set;

/// URL scheme safety independent of an output format. A policy is the caller's scheme
/// allow-list, not a universal HTML policy; each output backend selects the schemes it can
/// emit.
public final class UrlSafety {

  /// Schemes allowed in a URL destination. Relative URLs carry no scheme and always pass.
  public record SchemePolicy(Set<String> schemes) {
    public SchemePolicy {
      schemes = Set.copyOf(schemes);
    }

    public static SchemePolicy of(String... schemes) {
      return new SchemePolicy(Set.of(schemes));
    }
  }

  private UrlSafety() {}

  /// Percent-encodes the colon of a URL whose scheme is not in `policy`; allowed and relative
  /// URLs pass through unchanged. A defused URL also drops C0 controls and DEL, which browsers
  /// ignore and which could otherwise hide a scheme.
  public static String sanitizeScheme(String url, SchemePolicy policy) {
    if (hasSafeOrNoScheme(url, policy)) {
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

  // Relative, or an allowed scheme. A tab/CR/LF inside the scheme is unsafe: browsers strip it
  // before re-reading the scheme.
  private static boolean hasSafeOrNoScheme(String url, SchemePolicy policy) {
    var i = 0;
    while (i < url.length() && url.charAt(i) <= ' ') {
      i++;
    }
    var start = i;
    var dirty = false;
    for (; i < url.length(); i++) {
      var c = url.charAt(i);
      if (c == ':') {
        return !dirty && i > start && isAllowedScheme(url, start, i, policy);
      }
      if (c == '\t' || c == '\n' || c == '\r') {
        dirty = true;
      } else if (!isSchemeChar(c, i == start)) {
        return true; // no scheme before ':'
      }
    }
    return true;
  }

  private static boolean isAllowedScheme(String url, int start, int end, SchemePolicy policy) {
    for (var scheme : policy.schemes()) {
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
