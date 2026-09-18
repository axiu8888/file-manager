const STORAGE_KEY = 'kift.video.volume'
const DEFAULT_VOLUME = 0.5

export function getCachedVideoVolume(): number {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw == null || raw === '') return DEFAULT_VOLUME
    const n = Number(raw)
    if (!Number.isFinite(n)) return DEFAULT_VOLUME
    return Math.min(1, Math.max(0, n))
  } catch {
    return DEFAULT_VOLUME
  }
}

export function setCachedVideoVolume(volume: number) {
  try {
    const n = Math.min(1, Math.max(0, volume))
    localStorage.setItem(STORAGE_KEY, String(n))
  } catch {
    /* ignore quota / private mode */
  }
}

export function bindVideoVolume(el: HTMLMediaElement | null | undefined) {
  if (!el) return
  el.volume = getCachedVideoVolume()
  el.onvolumechange = () => {
    // 静音时仍记录当前 volume 滑块值，便于取消静音后恢复
    setCachedVideoVolume(el.volume)
  }
}
