package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.ast.LayoutSection;
import dev.nthings.adf4j.ast.UnknownBlock;
import dev.nthings.adf4j.ast.UnknownInline;
import dev.nthings.adf4j.ast.UnknownMark;
import dev.nthings.adf4j.internal.SourceIndex;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.ConversionEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ConversionEffects implements NodeVisitor {
  private static final Set<String> VISUAL =
      Set.of("textColor", "backgroundColor", "border", "fontSize", "alignment");
  private final List<ConversionEffect> effects = new ArrayList<>();
  private final SourceIndex source;
  private final ConversionOptions options;

  public ConversionEffects(AdfDocument doc, ConversionOptions options) {
    source = new SourceIndex(doc);
    this.options = options;
  }

  public List<ConversionEffect> effects() {
    return List.copyOf(effects);
  }

  public void visitBlock(AdfBlock node) {
    visit(node);
  }

  public void visitInline(AdfInline node) {
    visit(node);
  }

  public void visit(AdfNode node) {
    var path = source.path(node);
    if (node instanceof UnknownBlock || node instanceof UnknownInline) {
      if (options.unknownNodePolicy() == UnknownNodePolicy.SKIP)
        add(ConversionEffect.Kind.CONTENT_DROPPED, path, "Unknown node dropped");
      if (options.unknownNodePolicy() == UnknownNodePolicy.PLACEHOLDER)
        add(ConversionEffect.Kind.PLACEHOLDER, path, "Unknown node replaced with a placeholder");
    }
    if (node instanceof LayoutSection)
      add(
          ConversionEffect.Kind.STRUCTURE_CHANGED,
          path,
          "Columns flattened into sequential blocks");
    for (int i = 0; i < node.marks().size(); i++) {
      var mark = node.marks().get(i);
      var markPath = path + "/marks/" + i;
      if (mark instanceof UnknownMark)
        add(ConversionEffect.Kind.CONTENT_DROPPED, markPath, "Unknown mark dropped");
      else if (!options.htmlVisualMarks() && VISUAL.contains(mark.type()))
        add(ConversionEffect.Kind.STYLE_OMITTED, markPath, "Visual formatting omitted");
    }
  }

  private void add(ConversionEffect.Kind kind, String path, String message) {
    effects.add(new ConversionEffect(kind, path, message));
  }
}
