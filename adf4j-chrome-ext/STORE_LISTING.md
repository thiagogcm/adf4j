# Chrome Web Store Listing

## Dashboard fields

Title: adf4j Copy as Markdown

Summary: Copy Confluence Cloud pages to Markdown locally in your browser with adf4j.

Category: Workflow & Planning

Language: English

Support URL: <https://github.com/thiagogcm/adf4j/issues>

Privacy policy URL: <https://github.com/thiagogcm/adf4j/blob/main/adf4j-chrome-ext/PRIVACY.md>

## Detailed description

`adf4j Copy as Markdown` adds a small `Copy as markdown` button to Confluence Cloud pages. When clicked, it fetches the current published page body from Confluence, converts Atlassian Document Format to Markdown locally through the bundled `adf4j` WebAssembly runtime, and writes the generated Markdown to the clipboard.

The extension is built for teams that need to move Confluence Cloud content into Markdown-based tools, documentation repositories, issue trackers, or review workflows without sending page content to an external conversion service.

Key behavior:

- Works only on Confluence Cloud wiki pages under `https://*.atlassian.net/wiki/*`.
- Copies the published page body, not draft editor state.
- Converts locally in the browser using packaged WebAssembly.
- Uses the signed-in user's Confluence session to fetch the current page body and attachment metadata.
- Writes Markdown to the clipboard only after the user clicks the injected button.
- Does not include analytics, tracking, account login, payments, ads, or remote conversion services.

## Permission rationale

`clipboardWrite`: Required to write the generated Markdown to the clipboard after the user clicks `Copy as markdown`.

`https://*.atlassian.net/wiki/*`: Required to detect supported Confluence Cloud pages and fetch the current page body and attachment metadata from the user's Confluence tenant.

Content security policy: The extension keeps scripts local to the package. The `wasm-unsafe-eval` source is required for the bundled GraalVM WebAssembly runtime; no remote scripts are loaded.

## Reviewer test instructions

1. Load the unpacked extension from `adf4j-chrome-ext/dist` or install the submitted ZIP.
2. Sign in to a Confluence Cloud tenant (or any public space with anonymous access).
3. Open a published page under `https://*.atlassian.net/wiki/*`.
4. Confirm that the `Copy as markdown` button appears only when a page ID is present.
5. Click `Copy as markdown`.
6. Paste into a plain text editor and confirm the clipboard contains Markdown headed by the Confluence page title.
7. Optional failure check: sign out or use a page unavailable to the signed-in user, click the button, and confirm the button enters a readable failure state.

## Required assets checklist

- Extension icon: 128x128 PNG in the extension package. The packaged icon is declared in `manifest.json`.
- Screenshot: at least one 1280x800 or 640x400 screenshot showing the real Confluence page experience and the injected button.
- Small promotional tile: 440x280 image.

## Optional assets

- Additional screenshots: up to 5 total.
- Marquee promotional image: 1400x560 image.

## First-release scope notes

- No popup, side panel, account login, analytics, external service, payment flow, or localization is included.
- The release is Confluence Cloud-only.
- Store screenshots and promotional images are prepared outside this code package; this file records the required sizes and intended content.
