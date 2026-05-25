# ADF Test Resources

Executable ADF test data lives under `spec/`.

- Nested `spec/*/` directories contain auto-discovered conversion examples.
  Each input is a `*.json` file paired with at least one expected output using
  the same basename: `*.storage.md` or `*.presentation.html`.
  `AdfSpecConversionTests` discovers these pairs automatically.
- Use one canonical JSON input for a feature when storage and presentation only
  differ by render mode. Add both expected outputs next to that input instead
  of creating a parallel `presentation/` fixture.
- Top-level `spec/*.json` files are support and regression resources used by
  focused service tests when the behavior depends on non-default
  `RenderOptions`, large DB-derived payloads, invalid roots, or metadata
  assertions that are not simple conversion pairs.
