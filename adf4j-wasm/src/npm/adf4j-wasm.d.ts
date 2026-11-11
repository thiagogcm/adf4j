export interface Adf4jAttachment {
  fileId: string;
  title?: string | null;
  mediaType?: string | null;
  downloadUrl?: string | null;
}

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

export interface Adf4jDiagnostic {
  code: string;
  severity: 'INFO' | 'WARNING' | 'ERROR';
  message: string;
  /** JSON Pointers into the source document. */
  paths: string[];
  details: Record<string, string>;
}

export interface Adf4jPageTreeReference {
  macro: 'pagetree' | 'children';
  root: string | null;
  depth?: number;
  all: boolean;
  parameters: Record<string, string>;
}

export interface Adf4jExcerptReference {
  page: string;
  excerptName: string | null;
  parameters: Record<string, string>;
}

/** Source metadata; arbitrary custom renderer output is not analyzed. */
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

export interface Adf4jUnresolvedResource {
  kind: 'MEDIA' | 'ATTACHMENT' | 'PAGE' | 'PAGE_TREE' | 'EXCERPT' | 'ATTACHMENT_INVENTORY';
  id: string;
  path: string;
  reason: 'NO_ANSWER' | 'MISSING_INVENTORY' | 'CALLBACK_FAILED';
  fallback: string;
}

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

export type Adf4jResult = Outcome & {
  metadata: Adf4jMetadata;
  effects: Adf4jEffect[];
  unresolved: Adf4jUnresolvedResource[];
} & ({ ok: true; body: string; error?: never } | { ok: false; body: ''; error: string });

export type Adf4jAnalysisResult = Outcome & Status & { metadata: Adf4jMetadata };

export interface Adf4jWasmApi {
  /** Malformed ADF returns a failed outcome, never publishable Markdown. */
  convert(json: string, config?: Adf4jConfig): Adf4jResult;
  analyze(json: string, config?: Adf4jConfig): Adf4jAnalysisResult;
  version(): string;
}

export interface LoadAdf4jOptions {
  timeoutMs?: number;
  imageUrl?: string | URL;
  wasmUrl?: string | URL;
}

export function loadAdf4j(opts?: LoadAdf4jOptions): Promise<Adf4jWasmApi>;
