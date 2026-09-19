/** 多窗口 z-index 栈：快捷键 / Escape 只作用于最顶层 */
const windowZs = new Map<symbol, number>()

export function registerWindowZ(id: symbol, zIndex: number) {
  windowZs.set(id, zIndex || 0)
}

export function unregisterWindowZ(id: symbol) {
  windowZs.delete(id)
}

export function topmostWindowZ(): number {
  let max = 0
  for (const z of windowZs.values()) max = Math.max(max, z)
  return max
}

export function isTopmostWindow(zIndex: number | undefined | null): boolean {
  return (zIndex || 0) >= topmostWindowZ()
}
