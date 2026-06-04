package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.List;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;

import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;

/**
 * The HTML table-fallback renderer: builds a jsoup {@code <table>} for ADF tables GFM pipe syntax
 * can't express (colspan/rowspan, a number column, non-paragraph cell content, non-canonical header
 * placement). Split out of {@link TableRenderer} so the GFM and HTML paths are separate concerns.
 */
final class HtmlTableRenderer {

  private final MarkdownRenderingSupport markdownRenderingSupport;

  HtmlTableRenderer(MarkdownRenderingSupport markdownRenderingSupport) {
    this.markdownRenderingSupport = markdownRenderingSupport;
  }

  String renderHtmlTable(
      List<TableRow> rows, RendererState context, BlockRecursion recursion, boolean numberColumn) {
    var table = new Element(Tag.valueOf("table"), "");
    var dataRowIndex = 0;

    for (var row : rows) {
      var cells = row.content();
      if (cells.isEmpty()) {
        continue;
      }

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

      for (var cell : cells) {
        tableRow.appendChild(renderHtmlTableCell(cell, context, recursion));
      }
      table.appendChild(tableRow);
    }

    return HtmlFragments.outerHtml(table);
  }

  private Element renderHtmlTableCell(TableCell cell, RendererState context, BlockRecursion recursion) {
    var tag = cell.header() ? "th" : "td";
    var element = new Element(Tag.valueOf(tag), "");

    if (cell.colspan() > 1) {
      element.attr("colspan", Integer.toString(cell.colspan()));
    }
    if (cell.rowspan() > 1) {
      element.attr("rowspan", Integer.toString(cell.rowspan()));
    }

    var value =
        renderHtmlTableCellContent(cell.content(), context.withTableCell(TableCellKind.HTML), recursion);
    element.html(value);
    return element;
  }

  private String renderHtmlTableCellContent(
      List<AdfBlock> content, RendererState context, BlockRecursion recursion) {
    var renderedBlocks = new ArrayList<String>();
    for (var block : content) {
      var rendered = renderHtmlTableCellBlock(block, context, recursion);
      if (rendered != null && !rendered.isBlank()) {
        renderedBlocks.add(rendered);
      }
    }
    return String.join("<br>", renderedBlocks);
  }

  private String renderHtmlTableCellBlock(
      AdfBlock block, RendererState context, BlockRecursion recursion) {
    if (block instanceof BulletList bulletList) {
      return renderHtmlList(bulletList.content(), context, recursion, false);
    }
    if (block instanceof OrderedList orderedList) {
      return renderHtmlList(orderedList.content(), context, recursion, true);
    }
    return renderHtmlTableCellLeafBlock(block, context, recursion);
  }

  private String renderHtmlTableCellLeafBlock(
      AdfBlock block, RendererState context, BlockRecursion recursion) {
    var rendered =
        RenderBuffer.joinBlocks(recursion.renderBlock(block, context.withTableCell(TableCellKind.HTML)))
            .trim();
    if (rendered.isBlank()) {
      return "";
    }
    return markdownRenderingSupport.renderHtmlFragment(rendered);
  }

  private String renderHtmlList(
      List<ListItem> items, RendererState context, BlockRecursion recursion, boolean ordered) {
    var tag = ordered ? "ol" : "ul";
    var list = new Element(Tag.valueOf(tag), "");
    for (var item : items) {
      var rendered =
          renderHtmlListItem(item, context.withListDepth(context.listDepth() + 1), recursion);
      if (rendered != null) {
        list.appendChild(rendered);
      }
    }
    if (list.children().isEmpty()) {
      return "";
    }
    return HtmlFragments.outerHtml(list);
  }

  private Element renderHtmlListItem(ListItem item, RendererState context, BlockRecursion recursion) {
    var fragments = new ArrayList<String>();
    for (var block : item.content()) {
      var rendered =
          renderHtmlTableCellBlock(block, context.withTableCell(TableCellKind.HTML), recursion);
      if (rendered != null && !rendered.isBlank()) {
        fragments.add(rendered);
      }
    }
    if (fragments.isEmpty()) {
      return null;
    }
    var element = new Element(Tag.valueOf("li"), "");
    element.html(String.join("<br>", fragments));
    return element;
  }
}
