# Atlassian Document Format — Structure

Source: <https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/>
Fetched: 2026-05-25

## Overview

The Atlassian Document Format (ADF) is a system for representing rich text in Atlassian products. ADF represents rich text stored in Atlassian products, particularly in areas like Jira Cloud issue comments and textarea custom fields.

## JSON Schema

ADF documents are JSON objects validated against a schema available at `http://go.atlassian.com/adf-json-schema`. Note from Atlassian: "Marks and nodes included in the JSON schema may not be valid in this implementation."

## Core Structure

ADF documents use a hierarchical node system:

- **Nodes** — hierarchical elements that compose the document
- **Marks** — text formatting and embellishment applied to nodes
- **Ordering** — documents follow a single sequential path through content

### Example

```json
{
  "version": 1,
  "type": "doc",
  "content": [
    {
      "type": "paragraph",
      "content": [
        {
          "type": "text",
          "text": "Hello "
        },
        {
          "type": "text",
          "text": "world",
          "marks": [
            { "type": "strong" }
          ]
        }
      ]
    }
  ]
}
```

Renders as: "Hello **world**"

## Node Properties

| Property  | Required          | Description                            |
| --------- | ----------------- | -------------------------------------- |
| `type`    | yes               | Node category (e.g., paragraph, table) |
| `content` | yes (block nodes) | Array containing child nodes           |
| `version` | yes (root only)   | ADF version identifier                 |
| `marks`   | optional          | Text decoration/formatting             |
| `attrs`   | optional          | Additional node attributes             |

## Block Nodes

### Root

- `doc` — required container starting every document

### Top-level block nodes

- `blockquote`
- `bodiedSyncBlock`
- `bulletList`
- `codeBlock`
- `expand`
- `heading`
- `mediaGroup`
- `mediaSingle`
- `orderedList`
- `panel`
- `paragraph`
- `rule`
- `syncBlock`
- `table`
- `multiBodiedExtension`

### Child block nodes

- `blockTaskItem`
- `extensionFrame`
- `listItem`
- `media`
- `nestedExpand`
- `tableCell`
- `tableHeader`
- `tableRow`

## Inline Nodes

- `date`
- `emoji`
- `hardBreak`
- `inlineCard`
- `mention`
- `status`
- `text`
- `mediaInline`

## Mark Properties

| Property | Required | Description                      |
| -------- | -------- | -------------------------------- |
| `type`   | yes      | Mark category (code, link, etc.) |
| `attrs`  | optional | Mark-specific attributes         |

## Marks

- `border`
- `code`
- `em`
- `link`
- `strike`
- `strong`
- `subsup`
- `textColor`
- `underline`

## Additional resources (upstream)

- Document builder / playground: <https://developer.atlassian.com/cloud/jira/platform/apis/document/playground/>
- Per-node and per-mark reference pages (linked from the structure page above)
