/**
 * 直接触发浏览器下载（默认下载目录），不弹出「另存为」选路径。
 * 优先 data: URL，减少迅雷对 blob: 链接的劫持；过大则回退 blob:。
 */

const DATA_URL_MAX_BYTES = 32 * 1024 * 1024

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(reader.error || new Error('读取文件失败'))
    reader.readAsDataURL(blob)
  })
}

function triggerAnchorDownload(href: string, fileName: string) {
  const a = document.createElement('a')
  a.style.display = 'none'
  a.href = href
  a.setAttribute('download', fileName)
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

/** @returns true 已触发下载 */
export async function saveBlob(blob: Blob, fileName: string): Promise<boolean> {
  const name = (fileName || 'download').trim() || 'download'
  if (!blob || blob.size <= 0) {
    throw new Error('下载内容为空')
  }

  if (blob.size <= DATA_URL_MAX_BYTES) {
    const dataUrl = await blobToDataUrl(blob)
    if (!dataUrl.startsWith('data:')) {
      throw new Error('生成下载数据失败')
    }
    triggerAnchorDownload(dataUrl, name)
    return true
  }

  // 大文件：blob: + download，进入浏览器默认下载目录
  const url = URL.createObjectURL(blob)
  try {
    triggerAnchorDownload(url, name)
  } finally {
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
  }
  return true
}

/** 若接口把业务错误当成 blob 返回，解析并抛出 */
export async function assertDownloadBlob(blob: Blob): Promise<Blob> {
  const type = (blob.type || '').toLowerCase()
  const looksJsonType = type.includes('application/json') || type.includes('text/json')

  const tryParseError = async (full: Blob) => {
    const text = await full.text()
    try {
      const json = JSON.parse(text) as { code?: number; message?: string }
      if (typeof json.code === 'number' && json.code !== 0) {
        throw new Error(json.message || '下载失败')
      }
      if (typeof json.code === 'number' && json.code === 0) {
        throw new Error('下载失败：服务器返回了异常响应')
      }
    } catch (e) {
      if (e instanceof SyntaxError) {
        return new Blob([text], { type: blob.type || 'application/octet-stream' })
      }
      throw e
    }
    return full
  }

  if (looksJsonType) {
    return tryParseError(blob)
  }

  if (blob.size > 0 && blob.size < 4096) {
    const head = await blob.slice(0, Math.min(blob.size, 256)).text()
    const trimmed = head.trimStart()
    if (trimmed.startsWith('{') && /"code"\s*:/.test(trimmed)) {
      return tryParseError(blob)
    }
  }

  return blob
}
