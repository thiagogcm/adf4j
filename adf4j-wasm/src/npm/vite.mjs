// Optional Vite plugin: import adf4jWasm from '@nthings.dev/adf4j-wasm/vite'.
// Excludes the package from dependency pre-bundling, which would rewrite import.meta.url
// and break the loader's static new URL('./...', import.meta.url) asset references.

const PACKAGE_NAME = '@nthings.dev/adf4j-wasm';

export default function adf4jWasm() {
  return {
    name: 'adf4j-wasm',
    config() {
      return { optimizeDeps: { exclude: [PACKAGE_NAME] } };
    },
  };
}
