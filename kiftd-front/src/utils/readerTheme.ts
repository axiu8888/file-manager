/**
 * 电子书 / 文本阅读：字号与背景主题（本地持久化）
 */

export type ReaderBgId = 'paper' | 'sepia' | 'mint' | 'night'

export interface ReaderBgTheme {
  id: ReaderBgId
  label: string
  bg: string
  fg: string
  link: string
}

export const READER_BG_THEMES: ReaderBgTheme[] = [
  { id: 'paper', label: '白纸', bg: '#ffffff', fg: '#1f2937', link: '#0f766e' },
  { id: 'sepia', label: '羊皮纸', bg: '#f4ecd8', fg: '#5b4636', link: '#8b5e34' },
  { id: 'mint', label: '护眼绿', bg: '#e7f2e9', fg: '#1f3d2a', link: '#2f6b4f' },
  { id: 'night', label: '夜间', bg: '#1a1d24', fg: '#d1d5db', link: '#5eead4' },
]

/** 字号档位（px） */
export const READER_FONT_SIZES = [14, 16, 18, 20, 22, 26, 30] as const
export type ReaderFontSize = (typeof READER_FONT_SIZES)[number]

const FONT_KEY = 'kiftd.reader.fontSize'
const BG_KEY = 'kiftd.reader.bg'

export function getReaderBgTheme(id: ReaderBgId): ReaderBgTheme {
  return READER_BG_THEMES.find((t) => t.id === id) || READER_BG_THEMES[0]
}

export function loadReaderFontSize(): ReaderFontSize {
  try {
    const n = Number(localStorage.getItem(FONT_KEY))
    if (READER_FONT_SIZES.includes(n as ReaderFontSize)) return n as ReaderFontSize
  } catch {
    /* ignore */
  }
  return 18
}

export function saveReaderFontSize(size: ReaderFontSize) {
  try {
    localStorage.setItem(FONT_KEY, String(size))
  } catch {
    /* ignore */
  }
}

export function loadReaderBgId(): ReaderBgId {
  try {
    const raw = localStorage.getItem(BG_KEY)
    if (READER_BG_THEMES.some((t) => t.id === raw)) return raw as ReaderBgId
  } catch {
    /* ignore */
  }
  return 'paper'
}

export function saveReaderBgId(id: ReaderBgId) {
  try {
    localStorage.setItem(BG_KEY, id)
  } catch {
    /* ignore */
  }
}

export function nextReaderFontSize(current: number, dir: 1 | -1): ReaderFontSize {
  const list = READER_FONT_SIZES as unknown as number[]
  let idx = list.findIndex((s) => s === current)
  if (idx < 0) {
    idx = list.reduce((best, s, i) => (Math.abs(s - current) < Math.abs(list[best] - current) ? i : best), 0)
  }
  const next = Math.min(list.length - 1, Math.max(0, idx + dir))
  return list[next] as ReaderFontSize
}

/** 注入到 MOBI iframe / 文本预览的内联样式片段 */
export function readerContentCss(fontPx: number, theme: ReaderBgTheme): string {
  return `
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
a { color: ${theme.link} !important; }
img, svg, video {
  max-width: 100%;
  height: auto;
}
`
}

/** 应用到 epub.js Rendition */
export function applyEpubReaderTheme(rendition: any, fontPx: number, theme: ReaderBgTheme) {
  if (!rendition?.themes) return
  const pct = Math.round((fontPx / 16) * 100)
  try {
    rendition.themes.fontSize(`${pct}%`)
  } catch {
    /* ignore */
  }
  try {
    rendition.themes.register('kiftd-reader', {
      html: {
        background: `${theme.bg} !important`,
      },
      body: {
        background: `${theme.bg} !important`,
        color: `${theme.fg} !important`,
        'font-size': `${fontPx}px !important`,
        'line-height': '1.8 !important',
      },
      p: { color: `${theme.fg} !important` },
      div: { color: `${theme.fg} !important` },
      span: { color: `${theme.fg} !important` },
      li: { color: `${theme.fg} !important` },
      a: { color: `${theme.link} !important` },
    })
    rendition.themes.select('kiftd-reader')
  } catch {
    try {
      rendition.themes.override('color', theme.fg)
      rendition.themes.override('background', theme.bg)
      rendition.themes.override('background-color', theme.bg)
    } catch {
      /* ignore */
    }
  }
}
