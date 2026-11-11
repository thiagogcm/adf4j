package dev.nthings.adf4j;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.json.ResultJson;
import dev.nthings.adf4j.internal.parser.AdfParsingService;
import dev.nthings.adf4j.internal.writer.AdfJsonWriter;
import dev.nthings.adf4j.result.OperationResult;
import dev.nthings.adf4j.result.ParseResult;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Reads and writes ADF JSON. Thread-safe.
///
/// A parsed document writes back to the same JSON, ignoring key order, null-valued keys, empty
/// `attrs`, `content` or `marks`, and number formatting. Malformed JSON the AST cannot hold, such
/// as a non-object element in `content`, is reported by {@link #parse(String)} as an
/// `UNPRESERVED_JSON` warning and dropped on write. Duplicate keys keep the last value silently.
public final class AdfJson {

  /// Keys of a result's `metadata` object, in {@link #writeResult} output order.
  public static final List<String> METADATA_SECTIONS = ResultJson.SECTION_NAMES;

  private static final AdfParsingService PARSER = AdfParsingService.createDefault();
  private static final AdfJsonWriter WRITER = AdfJsonWriter.createDefault();

  private AdfJson() {}

  /// Parses ADF JSON with diagnostics. Never throws on bad input; blank or invalid JSON yields no
  /// document.
  public static ParseResult parse(@Nullable String adfJson) {
    return PARSER.parse(adfJson);
  }

  /// Writes a result as one-line portable CLI/WASM JSON.
  public static String writeResult(OperationResult result) {
    return writeResult(result, false);
  }

  /// Writes a result as portable CLI/WASM JSON. `pretty` indents by two spaces, with `\n` newlines
  /// on every platform.
  public static String writeResult(OperationResult result, boolean pretty) {
    return ResultJson.write(Objects.requireNonNull(result, "result"), pretty);
  }

  public static String write(AdfDocument document) {
    return WRITER.write(Objects.requireNonNull(document, "document"));
  }
}
