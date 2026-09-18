import { normalizeApiPrefix } from './prefix-normalize'

export { normalizeApiPrefix }

export const API_PREFIX = normalizeApiPrefix(import.meta.env.VITE_API_PREFIX)

export function apiUrl(path: string): string {
  const suffix = path.startsWith('/') ? path : `/${path}`
  return `${API_PREFIX}${suffix}`
}
