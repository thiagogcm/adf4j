package dev.nthings.adf4j.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A tiny getopt-style argument parser, kept reflection-free so the native/wasm images stay
 * reflection-free. Supports clustered short flags ({@code -cp}), {@code -oVALUE}/{@code --long=VALUE}
 * and their spaced forms, repeated options, and the {@code --} terminator; short flags are
 * case-sensitive, so {@code -V} and {@code -v} differ.
 */
final class Args {

  /** Declares the options a command accepts: which names are flags vs value-bearing, with aliases. */
  static final class Spec {
    private final Map<String, String> aliasToCanonical = new LinkedHashMap<>();
    private final Set<String> valueOptions = new LinkedHashSet<>();
    private final Set<String> boolOptions = new LinkedHashSet<>();

    /** A boolean flag, e.g. {@code flag("help", "-h", "--help")}. */
    Spec flag(String canonical, String... aliases) {
      register(canonical, aliases);
      boolOptions.add(canonical);
      return this;
    }

    /** A value-bearing option, e.g. {@code value("output", "-o", "--output")}. */
    Spec value(String canonical, String... aliases) {
      register(canonical, aliases);
      valueOptions.add(canonical);
      return this;
    }

    private void register(String canonical, String[] aliases) {
      for (var alias : aliases) {
        aliasToCanonical.put(alias, canonical);
      }
    }
  }

  private final Map<String, List<String>> values;
  private final Set<String> flags;
  private final List<String> positionals;

  private Args(Map<String, List<String>> values, Set<String> flags, List<String> positionals) {
    this.values = values;
    this.flags = flags;
    this.positionals = positionals;
  }

  static Args parse(List<String> tokens, Spec spec) {
    var values = new LinkedHashMap<String, List<String>>();
    var flags = new LinkedHashSet<String>();
    var positionals = new ArrayList<String>();
    var endOfOptions = false;

    for (var i = 0; i < tokens.size(); i++) {
      var token = tokens.get(i);
      if (endOfOptions || token.equals("-") || !token.startsWith("-")) {
        positionals.add(token);
      } else if (token.equals("--")) {
        endOfOptions = true;
      } else if (token.startsWith("--")) {
        i = parseLong(token, tokens, i, spec, values, flags);
      } else {
        i = parseShortCluster(token, tokens, i, spec, values, flags);
      }
    }
    return new Args(values, flags, positionals);
  }

  private static int parseLong(
      String token,
      List<String> tokens,
      int index,
      Spec spec,
      Map<String, List<String>> values,
      Set<String> flags) {
    var body = token.substring(2);
    var equals = body.indexOf('=');
    var name = equals < 0 ? body : body.substring(0, equals);
    var inlineValue = equals < 0 ? null : body.substring(equals + 1);
    var canonical = spec.aliasToCanonical.get("--" + name);
    if (canonical == null) {
      throw CliException.usage("unknown option '--" + name + "'");
    }
    if (spec.valueOptions.contains(canonical)) {
      String value;
      if (inlineValue != null) {
        value = inlineValue;
      } else if (index + 1 < tokens.size()) {
        value = tokens.get(++index);
      } else {
        throw CliException.usage("option '--" + name + "' requires a value");
      }
      values.computeIfAbsent(canonical, key -> new ArrayList<>()).add(value);
    } else {
      if (inlineValue != null) {
        throw CliException.usage("option '--" + name + "' does not take a value");
      }
      flags.add(canonical);
    }
    return index;
  }

  private static int parseShortCluster(
      String token,
      List<String> tokens,
      int index,
      Spec spec,
      Map<String, List<String>> values,
      Set<String> flags) {
    for (var j = 1; j < token.length(); j++) {
      var ch = token.charAt(j);
      var canonical = spec.aliasToCanonical.get("-" + ch);
      if (canonical == null) {
        throw CliException.usage("unknown option '-" + ch + "'");
      }
      if (spec.valueOptions.contains(canonical)) {
        var rest = token.substring(j + 1);
        String value;
        if (!rest.isEmpty()) {
          value = rest;
        } else if (index + 1 < tokens.size()) {
          value = tokens.get(++index);
        } else {
          throw CliException.usage("option '-" + ch + "' requires a value");
        }
        values.computeIfAbsent(canonical, key -> new ArrayList<>()).add(value);
        return index; // the rest of the cluster was consumed as this option's value
      }
      flags.add(canonical);
    }
    return index;
  }

  boolean has(String canonical) {
    return flags.contains(canonical);
  }

  /** The last value given for a repeated option, or {@code null} when absent. */
  String value(String canonical) {
    var list = values.get(canonical);
    return list == null || list.isEmpty() ? null : list.getLast();
  }

  String value(String canonical, String fallback) {
    var value = value(canonical);
    return value == null ? fallback : value;
  }

  /** Every value given for a repeated option, in order. */
  List<String> values(String canonical) {
    return values.getOrDefault(canonical, List.of());
  }

  List<String> positionals() {
    return positionals;
  }
}
