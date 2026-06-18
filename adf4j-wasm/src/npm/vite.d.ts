// Structural plugin type so consumers need no `vite` types to import this.
export default function adf4jWasm(): {
  name: string;
  config(): { optimizeDeps: { exclude: string[] } };
};
