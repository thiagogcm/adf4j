export interface Adf4jWasmApi {
  convert(json: string): string;
  convertJson(json: string): {
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
