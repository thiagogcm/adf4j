package dev.nthings.adf4j.internal.analyze;

import java.util.List;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.BlockCard;
import dev.nthings.adf4j.ast.BlockTaskItem;
import dev.nthings.adf4j.ast.Blockquote;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.Caption;
import dev.nthings.adf4j.ast.CodeBlock;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.EmbedCard;
import dev.nthings.adf4j.ast.Expand;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.ExtensionFrame;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.LayoutColumn;
import dev.nthings.adf4j.ast.LayoutSection;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.ast.MultiBodiedExtension;
import dev.nthings.adf4j.ast.NestedExpand;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Panel;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Rule;
import dev.nthings.adf4j.ast.SyncBlock;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;
import dev.nthings.adf4j.ast.TaskItem;
import dev.nthings.adf4j.ast.TaskList;
import dev.nthings.adf4j.ast.UnknownBlock;

import org.jspecify.annotations.Nullable;

/**
 * The analyze phase's one tree-walk: descends a document in order, handing every block and inline to
 * each {@link NodeVisitor}. The single home for "what are a node's children".
 */
final class AdfNodeWalker {

  private final List<NodeVisitor> visitors;

  private AdfNodeWalker(List<NodeVisitor> visitors) {
    this.visitors = visitors;
  }

  static void walk(@Nullable AdfDocument document, List<NodeVisitor> visitors) {
    if (document == null || visitors.isEmpty()) {
      return;
    }
    new AdfNodeWalker(visitors).blocks(document.content());
  }

  private void blocks(List<? extends AdfBlock> blocks) {
    for (var block : blocks) {
      block(block);
    }
  }

  private void block(AdfBlock block) {
    for (var visitor : visitors) {
      visitor.visitBlock(block);
    }
    switch (block) {
      case Paragraph node -> inlines(node.content());
      case Heading node -> inlines(node.content());
      case TaskItem node -> inlines(node.content());
      case DecisionItem node -> inlines(node.content());
      case Caption node -> inlines(node.content());
      case Blockquote node -> blocks(node.content());
      case Panel node -> blocks(node.content());
      case BulletList node -> blocks(node.content());
      case OrderedList node -> blocks(node.content());
      case ListItem node -> blocks(node.content());
      case TaskList node -> blocks(node.content());
      case BlockTaskItem node -> blocks(node.content());
      case DecisionList node -> blocks(node.content());
      case Table node -> blocks(node.content());
      case TableRow node -> blocks(node.content());
      case TableCell node -> blocks(node.content());
      case Expand node -> blocks(node.content());
      case NestedExpand node -> blocks(node.content());
      case LayoutSection node -> blocks(node.content());
      case LayoutColumn node -> blocks(node.content());
      case MediaSingle node -> blocks(node.content());
      case MediaGroup node -> blocks(node.content());
      case BodiedExtension node -> blocks(node.content());
      case MultiBodiedExtension node -> blocks(node.content());
      case ExtensionFrame node -> blocks(node.content());
      case BodiedSyncBlock node -> blocks(node.content());
      // Leaf blocks: no children. Exhaustive (no default) so new block types must be triaged here.
      case CodeBlock _, Rule _, Media _, Extension _, SyncBlock _, BlockCard _, EmbedCard _, UnknownBlock _ -> {
      }
    }
  }

  private void inlines(List<AdfInline> inlines) {
    for (var inline : inlines) {
      for (var visitor : visitors) {
        visitor.visitInline(inline);
      }
    }
  }
}
