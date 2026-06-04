package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;

/**
 * Renders ADF tables as GFM pipe tables and routes to {@link HtmlTableRenderer} when a table can't be
 * expressed as one. Owns the routing and the pipe-table layout (column widths, header mode, separator).
 */
final class TableRenderer {

  private final HtmlTableRenderer htmlTableRenderer;

  TableRenderer(MarkdownRenderingSupport markdownRenderingSupport) {
    this.htmlTableRenderer = new HtmlTableRenderer(markdownRenderingSupport);
  }

  String renderTable(Table node, RendererState context, BlockRecursion recursion) {
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
      return htmlTableRenderer.renderHtmlTable(rows, context, recursion, numberColumn);
    }
    if (firstRowIsHeader(rows)) {
      return renderGfmTable(rows, context, recursion, HeaderMode.NATURAL);
    }
    // GFM-safe but headerless: apply the fallback policy.
    return switch (context.tableFallback()) {
      case HTML -> htmlTableRenderer.renderHtmlTable(rows, context, recursion, numberColumn);
      case GFM_PROMOTE_FIRST_ROW -> renderGfmTable(rows, context, recursion, HeaderMode.PROMOTE_FIRST_ROW);
      case GFM_EMPTY_HEADER -> renderGfmTable(rows, context, recursion, HeaderMode.SYNTHESIZE_EMPTY);
    };
  }

  private enum HeaderMode { NATURAL, PROMOTE_FIRST_ROW, SYNTHESIZE_EMPTY }

  private String renderGfmTable(
      List<TableRow> rows, RendererState context, BlockRecursion recursion, HeaderMode mode) {
    var renderedRows = new ArrayList<Row>();
    var maxColumns = 0;

    for (var row : rows) {
      var rendered = renderTableRowCells(row, context, recursion);
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

  String renderTableRow(TableRow node, RendererState context, BlockRecursion recursion) {
    var rendered = renderTableRowCells(node, context, recursion);
    return rendered.cells().isEmpty() ? "" : "| " + String.join(" | ", rendered.cells()) + " |";
  }

  String renderTableCell(TableCell cell, RendererState context, BlockRecursion recursion) {
    var text =
        RenderBuffer.joinBlocks(recursion.renderBlocks(cell.content(), context.withTableCell(TableCellKind.GFM)))
            .trim();
    if (text.isBlank()) {
      return "";
    }
    return text.replace("|", "\\|").replace("\n", "<br>");
  }

  private Row renderTableRowCells(TableRow row, RendererState context, BlockRecursion recursion) {
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
      rendered.add(renderTableCell(cell, context, recursion));
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

  // Shared with HtmlTableRenderer: true when every cell in the row is a header cell.
  static boolean isHeaderRow(List<TableCell> cells) {
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
