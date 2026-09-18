/** 与后端 kiftd.api-prefix 保持一致（不含尾斜杠）。 */
export function normalizeApiPrefix(raw: string | undefined | null): string {
  let p = (raw ?? '/api').trim()
  if (!p || p === '/') p = '/api'
  if (!p.startsWith('/')) p = `/${p}`
  while (p.length > 1 && p.endsWith('/')) p = p.slice(0, -1)
  return p
}
