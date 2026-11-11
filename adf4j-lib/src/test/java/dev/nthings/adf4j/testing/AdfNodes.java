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
import dev.nthings.adf4j.ast.Text;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Raw AST construction for off-schema structures the type-checked {@link
// dev.nthings.adf4j.ast.Adf}
/// builder refuses, such as lists inside a `blockTaskItem`. Prefer `Adf` everywhere else.
public final class AdfNodes {

  private AdfNodes() {}

  public static Text text(String value) {
    return new Text(value, List.of());
  }

  public static Paragraph paragraph(String value) {
    return paragraphOf(List.of(text(value)));
  }

  public static Paragraph paragraphOf(List<AdfInline> content) {
    return new Paragraph(Attributes.empty(), content, List.of());
  }

  public static ListItem listItem(List<? extends AdfBlock> content) {
    return new ListItem(Attributes.empty(), List.copyOf(content), List.of());
  }

  public static BulletList bulletList(List<? extends AdfBlock> items) {
    return new BulletList(Attributes.empty(), List.copyOf(items), List.of());
  }

  public static OrderedList orderedList(int order, List<? extends AdfBlock> items) {
    return new OrderedList(new Attributes(Map.of("order", order)), List.copyOf(items), List.of());
  }

  public static TaskList taskList(List<? extends AdfBlock> items) {
    return new TaskList(Attributes.empty(), List.copyOf(items), List.of());
  }

  public static TaskItem taskItem(String state, List<AdfInline> content) {
    return new TaskItem(new Attributes(Map.of("state", state)), content, List.of());
  }

  public static BlockTaskItem blockTaskItem(String state, List<? extends AdfBlock> content) {
    return new BlockTaskItem(
        new Attributes(Map.of("state", state)), List.copyOf(content), List.of());
  }

  public static DecisionList decisionList(List<? extends AdfBlock> items) {
    return new DecisionList(Attributes.empty(), List.copyOf(items), List.of());
  }

  public static DecisionItem decisionItem(String state, List<AdfInline> content) {
    return new DecisionItem(new Attributes(Map.of("state", state)), content, List.of());
  }

  public static Panel panel(String panelType, List<? extends AdfBlock> content) {
    return new Panel(
        new Attributes(Map.of("panelType", panelType)), List.copyOf(content), List.of());
  }

  public static Blockquote blockquote(List<? extends AdfBlock> content) {
    return new Blockquote(Attributes.empty(), List.copyOf(content), List.of());
  }

  public static Expand expand(String title, List<? extends AdfBlock> content) {
    return new Expand(new Attributes(Map.of("title", title)), List.copyOf(content), List.of());
  }

  public static Table table(boolean numberColumn, List<? extends AdfBlock> rows) {
    var attrs = new Attributes(Map.of("isNumberColumnEnabled", numberColumn));
    return new Table(attrs, List.copyOf(rows), List.of());
  }

  public static TableRow tableRow(List<? extends AdfBlock> cells) {
    return new TableRow(Attributes.empty(), List.copyOf(cells), List.of());
  }

  public static TableCell tableCell(
      boolean header, int colspan, int rowspan, List<? extends AdfBlock> content) {
    var attrs = new LinkedHashMap<String, Object>();
    attrs.put("colspan", colspan);
    attrs.put("rowspan", rowspan);
    return new TableCell(header, new Attributes(attrs), List.copyOf(content), List.of());
  }

  public static BodiedExtension bodiedExtension(
      String extensionType, String extensionKey, List<? extends AdfBlock> content) {
    var attrs = new LinkedHashMap<String, Object>();
    attrs.put("extensionType", extensionType);
    attrs.put("extensionKey", extensionKey);
    return new BodiedExtension(new Attributes(attrs), List.copyOf(content), List.of());
  }

  public static ExtensionFrame extensionFrame(List<? extends AdfBlock> content) {
    return new ExtensionFrame(Attributes.empty(), List.copyOf(content), List.of());
  }

  public static BodiedSyncBlock bodiedSyncBlock(
      String resourceId, List<? extends AdfBlock> content) {
    var attrs = new Attributes(Map.of("resourceId", resourceId));
    return new BodiedSyncBlock(attrs, List.copyOf(content), List.of());
  }
}
