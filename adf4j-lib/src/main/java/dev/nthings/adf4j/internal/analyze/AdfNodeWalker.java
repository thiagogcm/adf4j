package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfNode;
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
import dev.nthings.adf4j.ast.TaskListContent;
import dev.nthings.adf4j.ast.UnknownBlock;
import dev.nthings.adf4j.internal.Located;
import java.util.List;

/// The analyze phase's one tree-walk: descends a document in order, handing every block and inline
/// with its JSON Pointer to each {@link NodeVisitor}. The single home for "what are a node's
/// children".
final class AdfNodeWalker {

  private final List<NodeVisitor> visitors;

  private AdfNodeWalker(List<NodeVisitor> visitors) {
    this.visitors = visitors;
  }

  static void walk(AdfDocument document, List<NodeVisitor> visitors) {
    new AdfNodeWalker(visitors).blocks(document, "", AdfBlock.class);
  }

  private void blocks(AdfNode parent, String path, Class<? extends AdfBlock> type) {
    for (var child : Located.children(parent, path, type)) {
      block(child.node(), child.path());
    }
  }

  private void block(AdfBlock block, String path) {
    for (var visitor : visitors) {
      visitor.visitBlock(block, path);
    }
    switch (block) {
      case Paragraph node -> inlines(node, path);
      case Heading node -> inlines(node, path);
      case TaskItem node -> inlines(node, path);
      case DecisionItem node -> inlines(node, path);
      case Caption node -> inlines(node, path);
      case BulletList node -> blocks(node, path, ListItem.class);
      case OrderedList node -> blocks(node, path, ListItem.class);
      case TaskList node -> blocks(node, path, TaskListContent.class);
      case DecisionList node -> blocks(node, path, DecisionItem.class);
      case Table node -> blocks(node, path, TableRow.class);
      case TableRow node -> blocks(node, path, TableCell.class);
      case LayoutSection node -> blocks(node, path, LayoutColumn.class);
      // Only the children that render: a stray child would feed references the body doesn't have.
      case MediaSingle node -> {
        blocks(node, path, Media.class);
        blocks(node, path, Caption.class);
      }
      case MediaGroup node -> blocks(node, path, Media.class);
      case Blockquote _,
          Panel _,
          ListItem _,
          BlockTaskItem _,
          TableCell _,
          Expand _,
          NestedExpand _,
          LayoutColumn _,
          BodiedExtension _,
          MultiBodiedExtension _,
          ExtensionFrame _,
          BodiedSyncBlock _ ->
          blocks(block, path, AdfBlock.class);
      // Leaf blocks: no children to analyze (a code block's text is literal, so its marks don't
      // count). Exhaustive (no default) so new block types must be triaged here.
      case CodeBlock _,
          Rule _,
          Media _,
          Extension _,
          SyncBlock _,
          BlockCard _,
          EmbedCard _,
          UnknownBlock _ -> {}
    }
  }

  private void inlines(AdfNode parent, String path) {
    for (var inline : Located.children(parent, path, AdfInline.class)) {
      for (var visitor : visitors) {
        visitor.visitInline(inline.node(), inline.path());
      }
    }
  }
}
