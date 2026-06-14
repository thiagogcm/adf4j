package dev.nthings.adf4j.internal.render;

import java.util.List;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfMark;

import org.jspecify.annotations.Nullable;

/**
 * The recursion entry points a delegate renderer needs to render its children, so it depends on these
 * four callbacks rather than the whole {@link AdfRenderer}. {@code AdfRenderer} implements it and hands
 * {@code this} to its delegates, breaking the renderer ⇄ delegate cycle down to one contract.
 */
interface BlockRecursion {

  // Renders one block to its (possibly multiple) output blocks.
  List<String> renderBlock(AdfBlock block, RendererState context);

  // Renders a sequence of blocks to a flat list of output blocks.
  List<String> renderBlocks(@Nullable List<AdfBlock> blocks, RendererState context);

  // Renders inline nodes to a string; startAtLineStart enables leading-block escaping at column 0.
  String renderInlineNodes(@Nullable List<AdfInline> nodes, RendererState context, boolean startAtLineStart);

  // Applies a node's marks to already-rendered text, within the given per-render context.
  String applyMarks(String text, List<AdfMark> marks, RenderContext context);
}
