package dev.nthings.adf4j.internal.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.nthings.adf4j.HeadingReference;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.confluence.ExcerptKey;

record MacroContext(
    Map<ExcerptKey, List<AdfBlock>> excerpts, List<HeadingReference> headings) {

  public MacroContext {
    excerpts = excerpts == null ? Map.of() : Map.copyOf(excerpts);
    headings = headings == null ? List.of() : List.copyOf(headings);
  }

  public static MacroContext from(
      Map<ExcerptKey, List<AdfBlock>> excerpts, List<HeadingReference> headings) {
    if (excerpts == null || excerpts.isEmpty()) {
      return new MacroContext(Map.of(), headings);
    }

    var converted = new HashMap<ExcerptKey, List<AdfBlock>>();
    excerpts.forEach(
        (key, blocks) -> {
          if (key == null) {
            return;
          }
          var safeBlocks = blocks == null ? List.<AdfBlock>of() : List.copyOf(blocks);
          converted.put(key, safeBlocks);
        });
    return new MacroContext(Map.copyOf(converted), headings);
  }
}
