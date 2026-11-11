/** An attachment known to the caller, used to resolve attachment references. */
export interface Adf4jAttachment {
  fileId: string;
  title?: string | null;
  mediaType?: string | null;
  downloadUrl?: string | null;
}

/** Rendering policy (`markdown`) and document context (`context`) passed to convert and analyze. */
export interface Adf4jConfig {
  markdown?: {
    unknownNodePolicy?: 'placeholder' | 'skip' | 'preserve-raw' | 'fail';
    tableFallback?: 'gfm-empty-header' | 'gfm-promote-first-row' | 'html';
    imageSizeAttributes?: boolean;
    htmlVisualMarks?: boolean;
    collapseHardBreaks?: boolean;
    escapeParentheses?: boolean;
  };
  context?: {
    title?: string | null;
    /** Absent means unknown; an empty array is an authoritative empty inventory. */
    attachments?: Adf4jAttachment[];
  };
}

/** A conversion or analysis finding. */
export interface Adf4jDiagnostic {
  code: string;
  severity: 'INFO' | 'WARNING' | 'ERROR';
  message: string;
  /** JSON Pointers into the source document. */
  paths: string[];
  details: Record<string, string>;
}

/** A page-tree macro (`pagetree` or `children`) found in the document. */
export interface Adf4jPageTreeReference {
  macro: 'pagetree' | 'children';
  root: string | null;
  depth?: number;
  all: boolean;
  parameters: Record<string, string>;
}

/** An excerpt-include macro referencing another page's excerpt. */
export interface Adf4jExcerptReference {
  page: string;
  excerptName: string | null;
  parameters: Record<string, string>;
}

/** Source metadata extracted from the document. Custom renderer output is not analyzed. */
export interface Adf4jMetadata {
  pageIds: string[];
  externalUrls: string[];
  referencedFileIds: string[];
  mentionRefs: { id: string | null; text: string | null }[];
  attachmentRefs: Adf4jAttachment[];
  pageTreeRefs: Adf4jPageTreeReference[];
  excerptRefs: Adf4jExcerptReference[];
  excerpts: { name: string | null }[];
  outline: { level: number; text: string; anchor: string }[];
}

/** A resource the caller could not resolve; `fallback` is what was rendered in its place. */
export interface Adf4jUnresolvedResource {
  kind: 'MEDIA' | 'ATTACHMENT' | 'PAGE' | 'PAGE_TREE' | 'EXCERPT' | 'ATTACHMENT_INVENTORY';
  id: string;
  path: string;
  reason: 'NO_ANSWER' | 'MISSING_INVENTORY' | 'CALLBACK_FAILED';
  fallback: string;
}

/** A visible change in the output, such as dropped content or a placeholder. */
export interface Adf4jEffect {
  kind: 'CONTENT_DROPPED' | 'PLACEHOLDER' | 'STYLE_OMITTED' | 'STRUCTURE_CHANGED';
  path: string;
  message: string;
}

interface Outcome {
  hasWarnings: boolean;
  diagnostics: Adf4jDiagnostic[];
}

type Status = { ok: true; error?: never } | { ok: false; error: string };

/** Conversion result. `ok: false` carries an `error` and an empty `body`. */
export type Adf4jResult = Outcome & {
  metadata: Adf4jMetadata;
  effects: Adf4jEffect[];
  unresolved: Adf4jUnresolvedResource[];
} & ({ ok: true; body: string; error?: never } | { ok: false; body: ''; error: string });

/** Analysis result: metadata and diagnostics, without a Markdown body. */
export type Adf4jAnalysisResult = Outcome & Status & { metadata: Adf4jMetadata };

export interface Adf4jWasmApi {
  /** Converts ADF JSON to Markdown. Malformed ADF returns `ok: false`, never a Markdown body. */
  convert(json: string, config?: Adf4jConfig): Adf4jResult;
  /** Analyzes ADF JSON without producing Markdown. */
  analyze(json: string, config?: Adf4jConfig): Adf4jAnalysisResult;
  /** The adf4j library version. */
  version(): string;
}

/** Options for loadAdf4j. `imageUrl`/`wasmUrl` override the bundled assets. */
export interface LoadAdf4jOptions {
  /** Milliseconds to wait for the module to signal ready. Defaults to 30000. */
  timeoutMs?: number;
  imageUrl?: string | URL;
  wasmUrl?: string | URL;
}

/** Loads the WebAssembly module once and resolves to the API. Later calls reuse the same load. */
export function loadAdf4j(opts?: LoadAdf4jOptions): Promise<Adf4jWasmApi>;
