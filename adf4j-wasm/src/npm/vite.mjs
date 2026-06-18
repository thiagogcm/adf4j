// Optional Vite plugin for adf4j-wasm.
//
//   import adf4jWasm from '@nthings.dev/adf4j-wasm/vite';
//   export default defineConfig({ plugins: [adf4jWasm()] });
//
// The package loads its .wasm through static `new URL('./asset', import.meta.url)` references, which
// Vite's dependency pre-bundler breaks by rewriting `import.meta.url` to its `.vite/deps/` cache.
// Excluding the package from pre-bundling keeps those references resolving; setting
// `optimizeDeps.exclude` by hand does the same thing.

const PACKAGE_NAME = '@nthings.dev/adf4j-wasm';

export default function adf4jWasm() {
  return {
    name: 'adf4j-wasm',
    config() {
      return { optimizeDeps: { exclude: [PACKAGE_NAME] } };
    },
  };
}
