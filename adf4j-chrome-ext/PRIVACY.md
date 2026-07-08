# Privacy Policy

Last updated: July 8, 2026

`adf4j Copy as Markdown` converts Confluence Cloud pages to Markdown locally in your browser.

## Data the extension reads

- The current Confluence Cloud page URL and page metadata, only on pages matching `https://*.atlassian.net/wiki/*`.
- The published page body from the signed-in user's Confluence tenant through Confluence REST v2.
- Attachment metadata for the current page, including attachment IDs, names, media types, and Confluence download URLs.

## Data the extension writes

- The generated Markdown is written to the user's clipboard only after the user clicks the `Copy as markdown` button.

## Data collection and sharing

The extension does not collect, sell, transmit, or store user data outside the browser. It does not use analytics, advertising, tracking pixels, remote logging, or any external conversion service. Conversion is performed locally through the bundled `adf4j` WebAssembly runtime.

## Network access

The extension uses the browser's existing Confluence session to fetch the current page body and attachment metadata from the user's Confluence Cloud tenant. It does not connect to any non-Confluence service for extension functionality.

## Permissions

- `clipboardWrite`: required to copy generated Markdown after the user clicks the button.
- `https://*.atlassian.net/wiki/*`: required to detect Confluence Cloud pages and fetch the current page body and attachment metadata.

## Support and security

For support, use GitHub issues: <https://github.com/thiagogcm/adf4j/issues>.

For security vulnerabilities, do not open a public issue. Use GitHub's private security advisory flow: <https://github.com/thiagogcm/adf4j/security/advisories/new>.
