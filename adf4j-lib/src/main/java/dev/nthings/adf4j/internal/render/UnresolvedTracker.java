package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.ArrayList;
import java.util.List;

/// Missing dependencies in source encounter order.
final class UnresolvedTracker {
  private final List<UnresolvedResource> resources = new ArrayList<>();

  void recordResource(UnresolvedResource resource) {
    resources.add(resource);
  }

  List<UnresolvedResource> build() {
    return List.copyOf(resources);
  }
}
