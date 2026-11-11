# Migrating to 2.0

2.0 separates operation success, warnings, conversion effects, and unresolved resources. Check success before publishing output. Parsing preserves imported ADF; schema validation is a separate operation.

## Java

| Previous API or behavior | 2.0 API or behavior |
| --- | --- |
| `ParseResult.isValid()` | `isSuccess()` means parsing produced a document; `requireDocument()` returns it or throws `AdfException`. |
| `MarkdownOptions` containing every conversion setting | `ConversionOptions` groups `MarkdownOptions`, `DocumentContext`, and `ResolverHooks`. Focused `withX` conveniences remain available. |
| `wasLossy()` | `isSuccess()`, `hasWarnings()`, `effects()`, and `unresolved()` answer separate questions. |
| `analyze(...)` returning metadata alone | `AnalysisResult` retains diagnostics; read `metadata()` after checking `isSuccess()`. JSON input is also accepted. |
| Blank input silently returning empty Markdown | `EMPTY_INPUT` error. Failed conversion has an empty body, even with a configured title. |
| `toMarkdown(...)` returning a string on invalid input | Throws `AdfException`. `convert(...)` returns failure as data; `convertOrThrow(...)` is explicit. |
| `UnknownNodePolicy.FAIL` throwing `UnknownNodeException` | Failed conversion with `UNKNOWN_NODE_REJECTED` and a JSON Pointer. |
| Resolver exceptions visible only in logs | `CALLBACK_FAILED` diagnostic; conversion can still succeed using a fallback. |
| Separate media and attachment lookup functions required | `withFileResolver(id -> url)` handles both. Specialized hooks take precedence. |
| Unresolved lookups grouped into page-only collections | One `List<UnresolvedResource>` identifies missing output dependencies, their reason, source pointer, and emitted fallback. |
| Extension callback returning `String` or `null` | Return `ExtensionOutput.replace(markdown)`, `header(markdown)`, or `defer()`. |
| Headerless tables promote the first data row | Default `GFM_EMPTY_HEADER` keeps all original rows as data. Promotion remains an explicit option. |

```java
import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.AdfValidation;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.DocumentContext;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.ResolverHooks;

var options = new ConversionOptions(
    MarkdownOptions.defaults(),
    DocumentContext.defaults().withTitle("Report"),
    ResolverHooks.defaults().withFileResolver(id -> "downloads/" + id));

var converter = AdfToMarkdown.with(options);
var result = converter.convert(adfJson);
result.requireSuccess();
String markdown = result.body();

var document = AdfJson.parse(adfJson).requireDocument();
AdfValidation.requireValid(document); // Before submitting generated/edited ADF.
```

`AdfValidation.validate(json)` checks the original JSON against the bundled ADF schema. `validate(document)` checks serialized AST content. `requireValid(document)` returns the document or throws. Schema conformance does not guarantee acceptance by a particular Atlassian product.

Authoring factories reject out-of-range heading/indentation levels, border sizes, and incompatible code marks. Contextual constraints, such as alignment inside a panel, are checked by `AdfValidation`. Raw records and parsing remain tolerant to preserve existing content.

## Quality and diagnostics

- `isSuccess()` reports whether the operation completed. Failure diagnostics have `ERROR` severity.
- `hasWarnings()` reports `WARNING` diagnostics only. It is not a success or losslessness check.
- `effects()` records supported changes such as omitted visual styling, dropped unknown content, placeholders, flattened columns, and synthesized/promoted table headers. It is not a proof of visual equivalence.
- `unresolved()` reports missing output dependencies, including files, attachment inventories, page-tree entries, and excerpts. A declined rewrite of an existing usable page URL is not an unresolved dependency.

Diagnostics expose `paths` as JSON Pointers and `details` for structured context. Java exceptions remain available through `cause()` but never enter JSON output. Metadata describes source content; arbitrary replacement Markdown from extension renderers is not analyzed. Source locations follow position in the document, so a node or mark instance reused in several places is reported at each of them.

## CLI

| Previous usage | 2.0 usage |
| --- | --- |
| `convert --fail-on-lossy` | `--fail-on-warning`, `--fail-on-changes`, or `--fail-on-unresolved`, according to the intended gate. |
| `validate` only checking the document envelope | Full schema validation by default; `--parse-only` requests tolerant parsing checks. |
| `analyze` returning bare metadata | `{ ok, hasWarnings, diagnostics, metadata, error? }`; `--select` filters inside `metadata`. |
| `validate -f json` returning `valid` | `ok`, `hasWarnings`, `diagnostics`, and `error` on failure. |
| Conversion JSON rendering excerpts a second time | Source excerpt names are included in metadata. The body contains the rendered document. |

`convert -f json` returns `ok`, `body`, `hasWarnings`, `diagnostics`, `metadata`, `effects`, `unresolved`, and `error` on failure. Content failures still produce JSON and exit 3. Quality gates exit 4 and retain the output for inspection.

Use `--config config.json` for portable rendering policy and document context. Explicit flags override supplied values; positive boolean flags enable their feature. Set booleans to false in the configuration when needed.

```json
{
  "markdown": {
    "unknownNodePolicy": "placeholder",
    "tableFallback": "gfm-empty-header"
  },
  "context": {
    "title": "Report",
    "attachments": [
      {
        "fileId": "file-1",
        "title": "guide.pdf",
        "downloadUrl": "downloads/guide.pdf"
      }
    ]
  }
}
```

See the [configuration schema](schemas/conversion-options.schema.json) and [operation result schema](schemas/operation-result.schema.json).

## JavaScript and TypeScript

The second argument is now configuration with `markdown` and `context` sections. `analyze` is available for planning fetches before conversion. Result types discriminate on `ok`; a failure always has `error`, and a failed conversion has an empty body.

```js
const config = {
  markdown: { collapseHardBreaks: true },
  context: { title: "Report", attachments },
};
const analysis = adf4j.analyze(adfJson, config);
if (!analysis.ok) throw new Error(analysis.error);

const result = adf4j.convert(adfJson, config);
if (!result.ok) throw new Error(result.error);
console.log(result.body);
```

Use `loadAdf4j({ imageUrl, wasmUrl })` for custom assets, with file URLs in Node. The `imagePath`/`wasmPath` aliases are removed. An explicit image URL is authoritative; a failure does not silently load another image. A loader timeout can be retried.
