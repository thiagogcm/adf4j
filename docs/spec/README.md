# ADF Spec Reference

Local snapshot of the official Atlassian Document Format spec, kept here for offline reference while developing `adf4j`. Treat the upstream sources as authoritative — re-fetch when you suspect drift.

## Files

- [`adf-schema.json`](./adf-schema.json) — official JSON Schema (draft-04), fetched from `https://go.atlassian.com/adf-json-schema`
- [`structure.md`](./structure.md) — human-readable structure overview from the Atlassian developer docs

## Provenance

- Fetched: 2026-05-25
- Schema source: <https://go.atlassian.com/adf-json-schema>
- Structure docs source: <https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/>

Note from Atlassian: "Marks and nodes included in the JSON schema may not be valid in this implementation." The schema lists everything ADF can express; individual products (Jira, Confluence, etc.) may accept a subset.

## Refreshing

```sh
curl -sL -o docs/spec/adf-schema.json https://go.atlassian.com/adf-json-schema
```

For the structure doc, re-fetch the page above and update `structure.md`.
