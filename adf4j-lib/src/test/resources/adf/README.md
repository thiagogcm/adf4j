# ADF Test Resources

`ConversionGoldenTests` drives everything here.

- `spec/**/*.json` are ADF fixtures: one canonical input per feature under `inline/`, `marks/` and `nodes/`, plus larger real-world and regression payloads at the top level. A sibling `*.md` holds the fixture's default Markdown body in readable form.
- `golden/corpus.tsv` holds off-spec and hostile inputs, one `name<TAB>json` per line.
- `golden/<preset>.txt` pins everything observable about each case under one option preset: body, diagnostics with paths and details, effects, unresolved dependencies, metadata, excerpts, and whether the written document conforms to the ADF schema (`schema: off-schema` marks fixtures that model product tolerance beyond it). Non-default presets list only the cases that differ from `defaults`.

To accept an intended change, run `./mvnw -pl adf4j-lib test -Dtest=ConversionGoldenTests -Dadf4j.updateGoldens=true` and review the diff.
