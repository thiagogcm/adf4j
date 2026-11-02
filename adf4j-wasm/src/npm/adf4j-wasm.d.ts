export interface Adf4jAttachment {
  /** The media services file UUID (`extensions.fileId` / v2 `fileId` in the Confluence REST API). */
  fileId: string;
  /** The attachment's file name. Optional. */
  title?: string;
  /** The attachment's MIME type. */
  mediaType?: string;
  /** The attachment's real URL; becomes the default link destination for matching nodes. */
  downloadUrl?: string;
}

/** Parsed as strictly as the CLI's `--attachments` file: an unknown key is an error, not ignored. */
export interface Adf4jConvertContext {
  /** The page's attachment inventory; a present (even empty) array is authoritative. */
  attachments?: Adf4jAttachment[];
  /** Prepended as an escaped level-1 heading; blank emits no heading. */
  title?: string;
}

export interface Adf4jDiagnostic {
  /** A stable machine-readable identifier, such as `INVALID_JSON`. */
  code: string;
  severity: 'INFO' | 'WARNING' | 'ERROR';
  message: string;
}

export interface Adf4jResult {
  /** False on invalid input or an invalid context; `error` then says why. */
  ok: boolean;
  /** The Markdown. Do not use it when `ok` is false. */
  body: string;
  /** True when any diagnostic is a WARNING or ERROR. */
  wasLossy: boolean;
  diagnostics: Adf4jDiagnostic[];
  error?: string;
}

export interface Adf4jWasmApi {
  /** Never throws on bad input: failures come back as `ok: false`. */
  convert(json: string, context?: Adf4jConvertContext | string): Adf4jResult;
  version(): string;
}

export interface LoadAdf4jOptions {
  timeoutMs?: number;
  imageUrl?: string | URL;
  imagePath?: string;
  wasmUrl?: string | URL;
  wasmPath?: string;
}

export function loadAdf4j(opts?: LoadAdf4jOptions): Promise<Adf4jWasmApi>;
