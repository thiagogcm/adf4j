package dev.nthings.adf4j;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.json.ResultJson;
import dev.nthings.adf4j.internal.parser.AdfParsingService;
import dev.nthings.adf4j.internal.writer.AdfJsonWriter;
import dev.nthings.adf4j.result.OperationResult;
import dev.nthings.adf4j.result.ParseResult;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Reads and writes ADF JSON. Thread-safe.
///
/// A parsed document writes back without loss: the output is the same JSON, ignoring key order,
/// null-valued keys, and empty `attrs`, `content` or `marks`, and comparing numbers by value. The
/// exception is malformed JSON the AST has no place for, such as a non-object element in `content`,
/// which {@link #parse(String)} reports as an `UNPRESERVED_JSON` warning. Duplicate keys are not
/// covered: the JSON reader keeps the last value, so the earlier ones are gone before the document
/// is built, with no warning.
public final class AdfJson {

  private static final AdfParsingService PARSER = AdfParsingService.createDefault();
  private static final AdfJsonWriter WRITER = AdfJsonWriter.createDefault();

  private AdfJson() {}

  /// Parses ADF JSON into an {@link AdfDocument} with diagnostics. Never throws on bad input: blank
  /// or invalid JSON yields a result with no document.
  public static ParseResult parse(@Nullable String adfJson) {
    return PARSER.parse(adfJson);
  }

  /// Writes an operation result using the portable CLI/WASM JSON contract.
  public static String writeResult(OperationResult result) {
    return ResultJson.write(result);
  }

  /// Writes a document as ADF JSON.
  public static String write(AdfDocument document) {
    return WRITER.write(Objects.requireNonNull(document, "document"));
  }
}
