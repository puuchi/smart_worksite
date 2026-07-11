export function useModuleMock(envName: string, fallback = false) {
  const value = import.meta.env[envName];
  if (value === 'true') return true;
  if (value === 'false') return false;
  return fallback || import.meta.env.VITE_USE_MOCK === 'true';
}
