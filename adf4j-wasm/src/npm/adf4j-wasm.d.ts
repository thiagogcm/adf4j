export interface Adf4jAttachment {
  /** The media services file UUID (`extensions.fileId` / v2 `fileId` in the Confluence REST API). */
  fileId: string;
  /** The attachment's file name. */
  title?: string;
  /** The attachment's MIME type. */
  mediaType?: string;
  /** The attachment's real URL; becomes the default link destination for matching nodes. */
  downloadUrl?: string;
}

export interface Adf4jConvertContext {
  /** The page's attachment inventory; a present (even empty) array is authoritative. */
  attachments?: Adf4jAttachment[];
}

export interface Adf4jWasmApi {
  convert(json: string, context?: Adf4jConvertContext | string): string;
  convertJson(
    json: string,
    context?: Adf4jConvertContext | string,
  ): {
    ok: boolean;
    lossy?: boolean;
    warnings?: number;
    errors?: number;
    body?: string;
    error?: string;
  };
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
