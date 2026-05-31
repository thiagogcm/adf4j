package dev.nthings.adf4j.confluence;

import java.util.Optional;

@FunctionalInterface
public interface PageTitleResolver {
  Optional<String> resolve(String pageNodeId);
}
