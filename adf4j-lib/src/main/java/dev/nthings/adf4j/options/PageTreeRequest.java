package dev.nthings.adf4j.options;

import java.util.Map;

/**
 * The context passed to a {@link PageTreeResolver}. {@code macro} says which macro triggered the call
 * (their defaults differ — see {@link PageTreeMacro}). {@code root} is the page the list is rooted at
 * — the {@code pagetree} macro's {@code root} parameter or the {@code children} macro's {@code page}
 * parameter, trimmed — or {@code null} when it is absent, blank, or an {@code @keyword} (e.g.
 * {@code @self}); the raw value stays reachable via {@code parameters}. {@code parameters} is the
 * macro's full, unmodifiable parameter map (e.g. {@code depth}, {@code all}, {@code startDepth}).
 */
public record PageTreeRequest(PageTreeMacro macro, String root, Map<String, String> parameters) {

  public PageTreeRequest {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
  }
}
