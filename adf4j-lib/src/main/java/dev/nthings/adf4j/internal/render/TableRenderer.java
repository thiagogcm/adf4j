package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.result.ConversionEffect;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/// Renders ADF tables as GFM pipe tables and routes to {@link HtmlTableRenderer} when a table
/// can't be expressed as one. Owns the routing and the pipe-table layout (column widths, header,
/// separator).
final class TableRenderer {

  private final BlockRenderer blockRenderer;
  private final HtmlTableRenderer htmlTableRenderer;

  TableRenderer(BlockRenderer blockRenderer, CellHtmlRenderer cellHtmlRenderer) {
    this.blockRenderer = blockRenderer;
    this.htmlTableRenderer = new HtmlTableRenderer(blockRenderer, cellHtmlRenderer);
  }

  String renderTable(Table node, RenderContext context) {
    var rows = node.rows();
    if (rows.isEmpty()) {
      return "";
    }

    var numberColumn = node.numberColumnEnabled();
    // Number column, colspan/rowspan, non-GFM cell content, or a non-canonical header placement
    // (header column, or header row that isn't first) can't be a GFM table.
    if (numberColumn
        || requiresHtmlTableFallback(rows)
        || (hasHeaderCell(rows) && !firstRowIsHeader(rows))) {
      return htmlTableRenderer.renderHtmlTable(node, context, numberColumn);
    }
    if (firstRowIsHeader(rows)) {
      return renderGfmTable(node, context, false);
    }
    // GFM-safe but headerless: apply the fallback policy. Promoting the first row needs no extra
    // step, because the first emitted row always sits above the separator.
    if (context.session().options().tableFallback() != TableFallback.HTML) {
      context
          .session()
          .events()
          .effect(
              ConversionEffect.Kind.STRUCTURE_CHANGED,
              context.path(),
              context.session().options().tableFallback() == TableFallback.GFM_EMPTY_HEADER
                  ? "Empty table header synthesized"
                  : "First data row promoted to header");
    }
    return switch (context.session().options().tableFallback()) {
      case HTML -> htmlTableRenderer.renderHtmlTable(node, context, numberColumn);
      case GFM_PROMOTE_FIRST_ROW -> renderGfmTable(node, context, false);
      case GFM_EMPTY_HEADER -> renderGfmTable(node, context, true);
    };
  }

  private String renderGfmTable(Table table, RenderContext context, boolean syntheticEmptyHeader) {
    var renderedRows = new ArrayList<List<String>>();
    var maxColumns = 0;

    for (var row : context.children(table, TableRow.class)) {
      var rendered = renderTableRowCells(row.node(), context.at(row));
      if (rendered.isEmpty()) {
        continue;
      }
      renderedRows.add(rendered);
      maxColumns = Math.max(maxColumns, rendered.size());
    }

    if (renderedRows.isEmpty()) {
      return "";
    }

    // GFM requires a header row, so give a headerless table an empty one.
    if (syntheticEmptyHeader) {
      renderedRows.addFirst(Collections.nCopies(maxColumns, ""));
    }

    final int columns = maxColumns;
    var normalizedRows = renderedRows.stream().map(row -> withPadding(row, columns)).toList();

    // GFM's delimiter row needs at least three dashes per column; cells pad to the same width.
    var widths = new int[columns];
    Arrays.fill(widths, 3);
    for (var row : normalizedRows) {
      for (var index = 0; index < row.size(); index++) {
        widths[index] = Math.max(widths[index], row.get(index).length());
      }
    }

    var separator = renderTableSeparator(widths);
    var lines = new ArrayList<String>();

    for (var rowIndex = 0; rowIndex < normalizedRows.size(); rowIndex++) {
      var row = normalizedRows.get(rowIndex);
      var padded = new ArrayList<String>();
      for (var index = 0; index < row.size(); index++) {
        padded.add(padRight(row.get(index), widths[index]));
      }
      lines.add("| " + String.join(" | ", padded) + " |");
      if (rowIndex == 0) {
        lines.add(separator);
      }
    }

    return String.join("\n", lines);
  }

  String renderTableRow(TableRow node, RenderContext context) {
    var rendered = renderTableRowCells(node, context);
    return rendered.isEmpty() ? "" : "| " + String.join(" | ", rendered) + " |";
  }

  String renderTableCell(TableCell cell, RenderContext context) {
    var text = blockRenderer.renderBlocks(cell, context.withSite(InlineSite.GFM_CELL)).trim();
    if (text.isBlank()) {
      return "";
    }
    return text.replace("|", "\\|").replace("\n", "<br>");
  }

  private List<String> renderTableRowCells(TableRow row, RenderContext context) {
    var rendered = new ArrayList<String>();
    for (var cell : context.children(row, TableCell.class)) {
      rendered.add(renderTableCell(cell.node(), context.at(cell)));
    }
    return rendered;
  }

  private boolean hasHeaderCell(List<TableRow> rows) {
    for (var row : rows) {
      for (var cell : row.cells()) {
        if (cell.header()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean firstRowIsHeader(List<TableRow> rows) {
    for (var row : rows) {
      var cells = row.cells();
      if (cells.isEmpty()) {
        continue;
      }
      return isHeaderRow(cells);
    }
    return false;
  }

  private String renderTableSeparator(int[] widths) {
    var columns = IntStream.of(widths).mapToObj("-"::repeat).toList();
    return "| " + String.join(" | ", columns) + " |";
  }

  private boolean requiresHtmlTableFallback(List<TableRow> rows) {
    for (var row : rows) {
      for (var cell : row.cells()) {
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

  private static List<String> withPadding(List<String> cells, int columns) {
    var padded = new ArrayList<>(cells);
    while (padded.size() < columns) {
      padded.add("");
    }
    return padded;
  }
}
