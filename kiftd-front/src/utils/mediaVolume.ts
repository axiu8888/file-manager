const VOLUME_KEY = 'kift.media.volume'
const MUTED_KEY = 'kift.media.muted'
const DEFAULT_VOLUME = 0.5

const bound = new WeakSet<HTMLMediaElement>()

export function getCachedVideoVolume(): number {
  try {
    const raw = localStorage.getItem(VOLUME_KEY) ?? localStorage.getItem('kift.video.volume')
    if (raw == null || raw === '') return DEFAULT_VOLUME
    const n = Number(raw)
    if (!Number.isFinite(n)) return DEFAULT_VOLUME
    return Math.min(1, Math.max(0, n))
  } catch {
    return DEFAULT_VOLUME
  }
}

export function getCachedMuted(): boolean {
  try {
    return localStorage.getItem(MUTED_KEY) === '1'
  } catch {
    return false
  }
}

export function setCachedVideoVolume(volume: number, muted?: boolean) {
  try {
    const n = Math.min(1, Math.max(0, volume))
    localStorage.setItem(VOLUME_KEY, String(n))
    localStorage.setItem('kift.video.volume', String(n))
    if (muted != null) localStorage.setItem(MUTED_KEY, muted ? '1' : '0')
  } catch {
    /* ignore quota / private mode */
  }
}

function remember(el: HTMLMediaElement) {
  setCachedVideoVolume(el.volume, el.muted)
}

/** 写回缓存值时不要再触发保存，避免浏览器把音量重置成 1 后覆盖用户设置 */
function applyCached(el: HTMLMediaElement) {
  const volume = getCachedVideoVolume()
  const muted = getCachedMuted()
  const mark = el as HTMLMediaElement & { __kiftApplyingVolume?: boolean }
  mark.__kiftApplyingVolume = true
  try {
    if (Math.abs(el.volume - volume) > 0.001) el.volume = volume
    if (el.muted !== muted) el.muted = muted
  } finally {
    queueMicrotask(() => {
      mark.__kiftApplyingVolume = false
    })
  }
}

function isApplying(el: HTMLMediaElement) {
  return !!(el as HTMLMediaElement & { __kiftApplyingVolume?: boolean }).__kiftApplyingVolume
}

/**
 * 记住音量/静音，并在换文件、元数据加载、开始播放时重新套用。
 * 部分浏览器会在 play 时把 volume 打回 1，这里会再写回缓存值。
 */
export function bindVideoVolume(el: HTMLMediaElement | null | undefined) {
  if (!el) return
  applyCached(el)
  if (bound.has(el)) return
  bound.add(el)

  el.addEventListener('volumechange', () => {
    if (isApplying(el)) return
    remember(el)
  })
  const restore = () => applyCached(el)
  el.addEventListener('loadedmetadata', restore)
  el.addEventListener('play', () => {
    restore()
    // play 之后浏览器仍可能异步把音量改回 1
    window.setTimeout(restore, 0)
  })
}
