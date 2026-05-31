package dev.nthings.adf4j.confluence;

import java.util.Optional;

@FunctionalInterface
public interface PageLinkResolver {
  Optional<String> resolve(String currentPageId, String targetPageId);
}
