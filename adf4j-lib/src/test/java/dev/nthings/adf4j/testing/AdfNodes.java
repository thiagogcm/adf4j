package dev.nthings.adf4j.testing;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.BlockTaskItem;
import dev.nthings.adf4j.ast.Blockquote;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.Expand;
import dev.nthings.adf4j.ast.ExtensionFrame;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Panel;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;
import dev.nthings.adf4j.ast.TaskItem;
import dev.nthings.adf4j.ast.TaskList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Terse AST construction for tests that build documents in code.
public final class AdfNodes {

  private AdfNodes() {}

  public static Paragraph paragraphOf(List<AdfInline> content) {
    return new Paragraph(null, content, null);
  }

  public static ListItem listItem(List<? extends AdfBlock> content) {
    return new ListItem(null, List.copyOf(content), null);
  }

  public static BulletList bulletList(List<? extends AdfBlock> items) {
    return new BulletList(null, List.copyOf(items), null);
  }

  public static OrderedList orderedList(int order, List<? extends AdfBlock> items) {
    return new OrderedList(new Attributes(Map.of("order", order)), List.copyOf(items), null);
  }

  public static TaskList taskList(List<? extends AdfBlock> items) {
    return new TaskList(null, List.copyOf(items), null);
  }

  public static TaskItem taskItem(String state, List<AdfInline> content) {
    return new TaskItem(new Attributes(Map.of("state", state)), content, null);
  }

  public static BlockTaskItem blockTaskItem(String state, List<? extends AdfBlock> content) {
    return new BlockTaskItem(new Attributes(Map.of("state", state)), List.copyOf(content), null);
  }

  public static DecisionList decisionList(List<? extends AdfBlock> items) {
    return new DecisionList(null, List.copyOf(items), null);
  }

  public static DecisionItem decisionItem(String state, List<AdfInline> content) {
    return new DecisionItem(new Attributes(Map.of("state", state)), content, null);
  }

  public static Panel panel(String panelType, List<? extends AdfBlock> content) {
    return new Panel(new Attributes(Map.of("panelType", panelType)), List.copyOf(content), null);
  }

  public static Blockquote blockquote(List<? extends AdfBlock> content) {
    return new Blockquote(null, List.copyOf(content), null);
  }

  public static Expand expand(String title, List<? extends AdfBlock> content) {
    return new Expand(new Attributes(Map.of("title", title)), List.copyOf(content), null);
  }

  public static Table table(boolean numberColumn, List<? extends AdfBlock> rows) {
    var attrs = new Attributes(Map.of("isNumberColumnEnabled", numberColumn));
    return new Table(attrs, List.copyOf(rows), null);
  }

  public static TableRow tableRow(List<? extends AdfBlock> cells) {
    return new TableRow(null, List.copyOf(cells), null);
  }

  public static TableCell tableCell(
      boolean header, int colspan, int rowspan, List<? extends AdfBlock> content) {
    var attrs = new LinkedHashMap<String, Object>();
    attrs.put("colspan", colspan);
    attrs.put("rowspan", rowspan);
    return new TableCell(header, new Attributes(attrs), List.copyOf(content), null);
  }

  public static BodiedExtension bodiedExtension(
      String extensionType, String extensionKey, List<? extends AdfBlock> content) {
    var attrs = new LinkedHashMap<String, Object>();
    attrs.put("extensionType", extensionType);
    attrs.put("extensionKey", extensionKey);
    return new BodiedExtension(new Attributes(attrs), List.copyOf(content), null);
  }

  public static ExtensionFrame extensionFrame(List<? extends AdfBlock> content) {
    return new ExtensionFrame(null, List.copyOf(content), null);
  }

  public static BodiedSyncBlock bodiedSyncBlock(
      String resourceId, List<? extends AdfBlock> content) {
    var attrs = new Attributes(Map.of("resourceId", resourceId));
    return new BodiedSyncBlock(attrs, List.copyOf(content), null);
  }
}
