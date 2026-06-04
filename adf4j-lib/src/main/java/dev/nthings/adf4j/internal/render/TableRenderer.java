package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;

import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;

final class TableRenderer {

  private final MarkdownRenderingSupport markdownRenderingSupport;

  TableRenderer(MarkdownRenderingSupport markdownRenderingSupport) {
    this.markdownRenderingSupport = markdownRenderingSupport;
  }

  String renderTable(Table node, RendererState context, AdfRenderer adfRenderer) {
    var rows = node.content();
    if (rows.isEmpty()) {
      return "";
    }

    var numberColumn = node.numberColumnEnabled();
    // Number column, colspan/rowspan, non-GFM cell content, or a non-canonical header placement
    // (header column, or header row that isn't first) can't be a GFM table.
    if (numberColumn
        || requiresHtmlTableFallback(rows)
        || (hasHeaderCell(rows) && !firstRowIsHeader(rows))) {
      return renderHtmlTable(rows, context, adfRenderer, numberColumn);
    }
    if (firstRowIsHeader(rows)) {
      return renderGfmTable(rows, context, adfRenderer, HeaderMode.NATURAL);
    }
    // GFM-safe but headerless: apply the fallback policy.
    return switch (context.tableFallback()) {
      case HTML -> renderHtmlTable(rows, context, adfRenderer, numberColumn);
      case GFM_PROMOTE_FIRST_ROW -> renderGfmTable(rows, context, adfRenderer, HeaderMode.PROMOTE_FIRST_ROW);
      case GFM_EMPTY_HEADER -> renderGfmTable(rows, context, adfRenderer, HeaderMode.SYNTHESIZE_EMPTY);
    };
  }

  private enum HeaderMode { NATURAL, PROMOTE_FIRST_ROW, SYNTHESIZE_EMPTY }

  private String renderGfmTable(
      List<TableRow> rows, RendererState context, AdfRenderer adfRenderer, HeaderMode mode) {
    var renderedRows = new ArrayList<Row>();
    var maxColumns = 0;

    for (var row : rows) {
      var rendered = renderTableRowCells(row, context, adfRenderer);
      if (rendered.cells().isEmpty()) {
        continue;
      }
      renderedRows.add(rendered);
      maxColumns = Math.max(maxColumns, rendered.cells().size());
    }

    if (renderedRows.isEmpty()) {
      return "";
    }

    // Make the first emitted row the GFM-required header. The synthetic cells are read-only
    // (withPadding returns them unchanged at exact column count), so an immutable list is safe.
    if (mode == HeaderMode.SYNTHESIZE_EMPTY) {
      renderedRows.add(0, new Row(Collections.nCopies(maxColumns, ""), true));
    } else if (mode == HeaderMode.PROMOTE_FIRST_ROW) {
      renderedRows.set(0, new Row(renderedRows.get(0).cells(), true));
    }

    final int columns = maxColumns;
    var normalizedRows = renderedRows.stream().map(row -> row.withPadding(columns)).toList();

    var widths = new int[maxColumns == 0 ? 1 : maxColumns];
    for (var row : normalizedRows) {
      for (var index = 0; index < row.cells().size(); index++) {
        widths[index] = Math.max(widths[index], row.cells().get(index).length());
      }
    }

    for (var index = 0; index < widths.length; index++) {
      widths[index] = Math.max(3, widths[index]);
    }

    var separator = renderTableSeparator(widths);
    var lines = new ArrayList<String>();

    for (var rowIndex = 0; rowIndex < normalizedRows.size(); rowIndex++) {
      var row = normalizedRows.get(rowIndex);
      var padded = new ArrayList<String>();
      for (var index = 0; index < row.cells().size(); index++) {
        padded.add(padRight(row.cells().get(index), widths[index]));
      }
      lines.add("| " + String.join(" | ", padded) + " |");
      if (rowIndex == 0) {
        lines.add(separator);
      }
    }

    return String.join("\n", lines);
  }

  String renderTableRow(TableRow node, RendererState context, AdfRenderer adfRenderer) {
    var rendered = renderTableRowCells(node, context, adfRenderer);
    return rendered.cells().isEmpty() ? "" : "| " + String.join(" | ", rendered.cells()) + " |";
  }

  String renderTableCell(TableCell cell, RendererState context, AdfRenderer adfRenderer) {
    var text = adfRenderer
        .joinBlocks(adfRenderer.renderBlocks(cell.content(), context.withTableCell(TableCellKind.GFM)))
        .trim();
    if (text.isBlank()) {
      return "";
    }
    return text.replace("|", "\\|").replace("\n", "<br>");
  }

  private Row renderTableRowCells(TableRow row, RendererState context, AdfRenderer adfRenderer) {
    var cells = row.content();
    if (cells.isEmpty()) {
      return new Row(List.of(), false);
    }
    var header = false;
    var rendered = new ArrayList<String>();
    for (var cell : cells) {
      if (cell.header()) {
        header = true;
      }
      rendered.add(renderTableCell(cell, context, adfRenderer));
    }
    return new Row(rendered, header);
  }

  private boolean hasHeaderCell(List<TableRow> rows) {
    for (var row : rows) {
      for (var cell : row.content()) {
        if (cell.header()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean firstRowIsHeader(List<TableRow> rows) {
    for (var row : rows) {
      var cells = row.content();
      if (cells.isEmpty()) {
        continue;
      }
      return isHeaderRow(cells);
    }
    return false;
  }

  private String renderTableSeparator(int[] widths) {
    var columns = IntStream.of(widths).mapToObj(width -> "-".repeat(Math.max(3, width))).toList();
    return "| " + String.join(" | ", columns) + " |";
  }

  private boolean requiresHtmlTableFallback(List<TableRow> rows) {
    for (var row : rows) {
      for (var cell : row.content()) {
        if (cell.colspan() > 1 || cell.rowspan() > 1) {
          return true;
        }
        for (var child : cell.content()) {
          if (!isGfmCellSafe(child)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean isGfmCellSafe(AdfBlock block) {
    return block instanceof Paragraph
        || block instanceof MediaSingle
        || block instanceof MediaGroup
        || block instanceof Media;
  }

  private String renderHtmlTable(
      List<TableRow> rows, RendererState context, AdfRenderer adfRenderer, boolean numberColumn) {
    var table = new Element(Tag.valueOf("table"), "");
    var dataRowIndex = 0;

    for (var row : rows) {
      var cells = row.content();
      if (cells.isEmpty()) {
        continue;
      }

      var tableRow = new Element(Tag.valueOf("tr"), "");
      var rowIsHeader = isHeaderRow(cells);

      if (numberColumn) {
        var leading = new Element(Tag.valueOf(rowIsHeader ? "th" : "td"), "");
        if (!rowIsHeader) {
          dataRowIndex++;
          leading.text(Integer.toString(dataRowIndex));
        }
        tableRow.appendChild(leading);
      }

      for (var cell : cells) {
        tableRow.appendChild(renderHtmlTableCell(cell, context, adfRenderer));
      }
      table.appendChild(tableRow);
    }

    return HtmlFragments.outerHtml(table);
  }

  private boolean isHeaderRow(List<TableCell> cells) {
    if (cells.isEmpty()) {
      return false;
    }
    for (var cell : cells) {
      if (!cell.header()) {
        return false;
      }
    }
    return true;
  }

  private Element renderHtmlTableCell(
      TableCell cell, RendererState context, AdfRenderer adfRenderer) {
    var tag = cell.header() ? "th" : "td";
    var element = new Element(Tag.valueOf(tag), "");

    if (cell.colspan() > 1) {
      element.attr("colspan", Integer.toString(cell.colspan()));
    }
    if (cell.rowspan() > 1) {
      element.attr("rowspan", Integer.toString(cell.rowspan()));
    }

    var value = renderHtmlTableCellContent(cell.content(), context.withTableCell(TableCellKind.HTML), adfRenderer);
    element.html(value);
    return element;
  }

  private String renderHtmlTableCellContent(
      List<AdfBlock> content, RendererState context, AdfRenderer adfRenderer) {
    var renderedBlocks = new ArrayList<String>();
    for (var block : content) {
      var rendered = renderHtmlTableCellBlock(block, context, adfRenderer);
      if (rendered != null && !rendered.isBlank()) {
        renderedBlocks.add(rendered);
      }
    }
    return String.join("<br>", renderedBlocks);
  }

  private String renderHtmlTableCellBlock(
      AdfBlock block, RendererState context, AdfRenderer adfRenderer) {
    if (block instanceof BulletList bulletList) {
      return renderHtmlList(bulletList.content(), context, adfRenderer, false);
    }
    if (block instanceof OrderedList orderedList) {
      return renderHtmlList(orderedList.content(), context, adfRenderer, true);
    }
    return renderHtmlTableCellLeafBlock(block, context, adfRenderer);
  }

  private String renderHtmlTableCellLeafBlock(
      AdfBlock block, RendererState context, AdfRenderer adfRenderer) {
    var rendered = adfRenderer.joinBlocks(adfRenderer.renderBlock(block, context.withTableCell(TableCellKind.HTML))).trim();
    if (rendered.isBlank()) {
      return "";
    }
    return renderHtmlFragment(rendered);
  }

  private String renderHtmlFragment(String markdown) {
    return markdownRenderingSupport.renderHtmlFragment(markdown);
  }

  private String renderHtmlList(
      List<ListItem> items, RendererState context, AdfRenderer adfRenderer, boolean ordered) {
    var tag = ordered ? "ol" : "ul";
    var list = new Element(Tag.valueOf(tag), "");
    for (var item : items) {
      var rendered = renderHtmlListItem(item, context.withListDepth(context.listDepth() + 1), adfRenderer);
      if (rendered != null) {
        list.appendChild(rendered);
      }
    }
    if (list.children().isEmpty()) {
      return "";
    }
    return HtmlFragments.outerHtml(list);
  }

  private Element renderHtmlListItem(
      ListItem item, RendererState context, AdfRenderer adfRenderer) {
    var fragments = new ArrayList<String>();
    for (var block : item.content()) {
      var rendered = renderHtmlTableCellBlock(block, context.withTableCell(TableCellKind.HTML), adfRenderer);
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

  private String padRight(String value, int width) {
    if (value.length() >= width) {
      return value;
    }
    return value + " ".repeat(width - value.length());
  }

  private record Row(List<String> cells, boolean header) {

    Row withPadding(int columns) {
      if (cells.size() >= columns) {
        return this;
      }
      var padded = new ArrayList<>(cells);
      while (padded.size() < columns) {
        padded.add("");
      }
      return new Row(padded, header);
    }
  }
}
