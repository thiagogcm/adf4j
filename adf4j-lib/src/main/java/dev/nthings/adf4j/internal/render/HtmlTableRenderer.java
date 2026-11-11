package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;
import java.util.ArrayList;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;

/// The HTML table-fallback renderer: builds a jsoup `<table>` for ADF tables GFM pipe syntax can't
/// express (colspan/rowspan, a number column, non-paragraph cell content, non-canonical header
/// placement). Split out of {@link TableRenderer} so the GFM and HTML paths are separate concerns.
final class HtmlTableRenderer {

  private final BlockRenderer blockRenderer;
  private final CellHtmlRenderer cellHtmlRenderer;

  HtmlTableRenderer(BlockRenderer blockRenderer, CellHtmlRenderer cellHtmlRenderer) {
    this.blockRenderer = blockRenderer;
    this.cellHtmlRenderer = cellHtmlRenderer;
  }

  String renderHtmlTable(Table node, RenderContext context, boolean numberColumn) {
    var table = new Element(Tag.valueOf("table"), "");
    var dataRowIndex = 0;

    for (var row : context.children(node, TableRow.class)) {
      var cells = row.node().cells();
      if (cells.isEmpty()) {
        continue;
      }
      var rowContext = context.at(row);

      var tableRow = new Element(Tag.valueOf("tr"), "");
      var rowIsHeader = TableRenderer.isHeaderRow(cells);

      if (numberColumn) {
        var leading = new Element(Tag.valueOf(rowIsHeader ? "th" : "td"), "");
        if (!rowIsHeader) {
          dataRowIndex++;
          leading.text(Integer.toString(dataRowIndex));
        }
        tableRow.appendChild(leading);
      }

      for (var cell : rowContext.children(row.node(), TableCell.class)) {
        tableRow.appendChild(renderHtmlTableCell(cell.node(), rowContext.at(cell)));
      }
      table.appendChild(tableRow);
    }

    return HtmlFragments.outerHtml(table);
  }

  private Element renderHtmlTableCell(TableCell cell, RenderContext context) {
    var tag = cell.header() ? "th" : "td";
    var element = new Element(Tag.valueOf(tag), "");

    if (cell.colspan() > 1) {
      element.attr("colspan", Integer.toString(cell.colspan()));
    }
    if (cell.rowspan() > 1) {
      element.attr("rowspan", Integer.toString(cell.rowspan()));
    }

    element.html(joinCellBlocks(cell, context.withSite(InlineSite.HTML_CELL)));
    return element;
  }

  // The block children of `parent`, the node `context` sits at, as cell HTML.
  private String joinCellBlocks(AdfNode parent, RenderContext context) {
    var renderedBlocks = new ArrayList<String>();
    for (var block : context.children(parent, AdfBlock.class)) {
      var rendered = renderHtmlTableCellBlock(block.node(), context.at(block));
      if (!rendered.isBlank()) {
        renderedBlocks.add(rendered);
      }
    }
    return String.join("<br>", renderedBlocks);
  }

  private String renderHtmlTableCellBlock(AdfBlock block, RenderContext context) {
    if (block instanceof BulletList bulletList) {
      return renderHtmlList(bulletList, context, false, 1);
    }
    if (block instanceof OrderedList orderedList) {
      return renderHtmlList(orderedList, context, true, orderedList.order());
    }
    var rendered = blockRenderer.renderBlock(block, context).trim();
    return rendered.isBlank() ? "" : cellHtmlRenderer.render(rendered);
  }

  private String renderHtmlList(AdfNode items, RenderContext context, boolean ordered, int start) {
    var tag = ordered ? "ol" : "ul";
    var list = new Element(Tag.valueOf(tag), "");
    if (ordered && start != 1) {
      list.attr("start", Integer.toString(start));
    }
    for (var item : context.children(items, ListItem.class)) {
      list.appendChild(renderHtmlListItem(item.node(), context.at(item)));
    }
    if (list.children().isEmpty()) {
      return "";
    }
    return HtmlFragments.outerHtml(list);
  }

  private Element renderHtmlListItem(ListItem item, RenderContext context) {
    var element = new Element(Tag.valueOf("li"), "");
    element.html(joinCellBlocks(item, context));
    return element;
  }
}
