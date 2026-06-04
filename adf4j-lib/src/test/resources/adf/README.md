# ADF Test Resources

Executable ADF test data lives under `spec/`.

- Nested `spec/*/` directories contain auto-discovered conversion examples. Each input is a `*.json` file paired with an expected Markdown output using the same basename: `*.md`. `AdfSpecConversionTests` discovers these pairs automatically.
- Use one canonical JSON input per feature and place its expected `*.md` output next to it.
- Top-level `spec/*.json` files are support and regression resources used by focused service tests when the behavior depends on non-default `RenderOptions`, large DB-derived payloads, invalid roots, or metadata assertions that are not simple conversion pairs.
- A small number of fixtures intentionally model non-schema ADF that the parser accepts for product tolerance or degradation coverage:
  - `spec/unknown-node-policy.json`
  - `spec/inline/embed-card-as-inline-link.json`
  - `spec/inline/inline-block-card.json`
  - `spec/marks/breakout-fragment-dataconsumer-passthrough.json`
  - `spec/nodes/code-block-strips-marks.json`
  - `spec/nodes/list-item-with-decision-sublist.json`
  - `spec/nodes/multi-bodied-extension.json`
