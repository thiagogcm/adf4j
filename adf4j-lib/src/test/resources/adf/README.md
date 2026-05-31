# ADF Test Resources

Executable ADF test data lives under `spec/`.

- Nested `spec/*/` directories contain auto-discovered conversion examples.
  Each input is a `*.json` file paired with an expected Markdown output using
  the same basename: `*.md`. `AdfSpecConversionTests` discovers these pairs
  automatically.
- Use one canonical JSON input per feature and place its expected `*.md` output
  next to it.
- Top-level `spec/*.json` files are support and regression resources used by
  focused service tests when the behavior depends on non-default
  `RenderOptions`, large DB-derived payloads, invalid roots, or metadata
  assertions that are not simple conversion pairs.
