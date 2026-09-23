/**
 * MOBI / AZW / AZW3 电子书打开工具（浏览器端解析）。
 */
import { initKf8File, initMobiFile } from '@lingo-reader/mobi-parser'

export interface MobiChapterContent {
  html: string
  css: { id: string; href: string }[]
}

export interface MobiTocNode {
  label: string
  href: string
  children?: MobiTocNode[]
}

export interface OpenedMobiBook {
  title: string
  spineIds: string[]
  toc: MobiTocNode[]
  loadChapter: (id: string) => MobiChapterContent | undefined
  resolveHref: (href: string) => { id: string; selector: string } | undefined
  destroy: () => void
}

function wrapBook(book: {
  getMetadata: () => { title?: string }
  getSpine: () => { id: string }[]
  getToc: () => MobiTocNode[]
  loadChapter: (id: string) => MobiChapterContent | undefined
  resolveHref: (href: string) => { id: string; selector: string } | undefined
  destroy: () => void
}): OpenedMobiBook {
  const meta = book.getMetadata?.() || {}
  const spine = book.getSpine?.() || []
  return {
    title: String(meta.title || '').trim(),
    spineIds: spine.map((c) => String(c.id)),
    toc: book.getToc?.() || [],
    loadChapter: (id) => book.loadChapter(id),
    resolveHref: (href) => book.resolveHref(href),
    destroy: () => {
      try {
        book.destroy()
      } catch {
        /* ignore */
      }
    },
  }
}

function looksLikePalmDb(buf: Uint8Array): boolean {
  if (buf.byteLength < 68) return false
  // PalmDB type/creator at 60..67 often "BOOKMOBI"
  const type = String.fromCharCode(...buf.slice(60, 68))
  return type.includes('BOOK') || type.includes('MOBI') || type.includes('TEXt')
}

/**
 * 打开 .mobi / .azw / .azw3。优先按 KF8（AZW3）解析，失败再回退经典 MOBI。
 */
export async function openMobiBook(
  data: ArrayBuffer | Uint8Array,
): Promise<OpenedMobiBook> {
  const buf = data instanceof Uint8Array ? data : new Uint8Array(data)
  if (buf.byteLength < 68) {
    throw new Error('电子书文件无效或为空')
  }
  if (!looksLikePalmDb(buf)) {
    throw new Error('不是有效的 MOBI/AZW 文件')
  }

  const tryKf8 = async () => {
    const kf8 = await initKf8File(buf)
    const spine = kf8.getSpine?.() || []
    if (!spine.length) {
      kf8.destroy()
      throw new Error('KF8 无章节内容')
    }
    return wrapBook(kf8)
  }

  const tryMobi = async () => {
    const mobi = await initMobiFile(buf)
    const spine = mobi.getSpine?.() || []
    if (!spine.length) {
      mobi.destroy()
      throw new Error('MOBI 无章节内容')
    }
    return wrapBook(mobi)
  }

  // 优先 KF8（含双格式 .mobi / .azw3），失败再回退经典 MOBI
  try {
    return await tryKf8()
  } catch (e1) {
    try {
      return await tryMobi()
    } catch {
      throw e1 instanceof Error ? e1 : new Error('MOBI 打开失败')
    }
  }
}

export function buildMobiChapterDocument(
  chapter: MobiChapterContent,
  opts?: { fontPx?: number; theme?: { bg: string; fg: string; link: string } },
): string {
  const css = (chapter.css || [])
    .map((c) => `<link rel="stylesheet" href="${c.href}">`)
    .join('')
  const fontPx = opts?.fontPx ?? 18
  const theme = opts?.theme ?? { bg: '#ffffff', fg: '#1f2937', link: '#0f766e' }
  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
${css}
<style>
  html, body {
    margin: 0;
    padding: 0;
    background: ${theme.bg} !important;
    color: ${theme.fg} !important;
  }
  body {
    padding: 28px 36px 48px;
    font-size: ${fontPx}px !important;
    line-height: 1.8;
    font-family: "Source Han Serif SC", "Noto Serif SC", "Songti SC", Georgia, "Times New Roman", serif;
    word-break: break-word;
  }
  p, div, span, li, td, th, h1, h2, h3, h4, h5, h6 {
    color: inherit;
  }
  img, svg, video {
    max-width: 100%;
    height: auto;
  }
  a { color: ${theme.link} !important; }
</style>
</head>
<body>${chapter.html || ''}</body>
</html>`
}
