/**
 * 基于 Web Audio 的音乐音效引擎：EQ + 立体声拓宽 + 轻压缩。
 * HiFi = 近似直通（平坦 EQ、无拓宽）。
 */

export type AudioFxPresetId =
  | 'hifi'
  | 'bass'
  | 'surround3d'
  | 'vocal'
  | 'rock'
  | 'electronic'
  | 'soft'
  | 'bright'

export interface AudioFxPreset {
  id: AudioFxPresetId
  label: string
  hint: string
  bass: number
  lowMid: number
  mid: number
  highMid: number
  treble: number
  widen: number
  punch: number
}

export const AUDIO_FX_PRESETS: AudioFxPreset[] = [
  {
    id: 'hifi',
    label: 'HiFi',
    hint: '直通还原，少处理',
    bass: 0,
    lowMid: 0,
    mid: 0,
    highMid: 0,
    treble: 0,
    widen: 0,
    punch: 0,
  },
  {
    id: 'bass',
    label: '超重低音',
    hint: '低频明显加厚，鼓点更沉',
    bass: 14,
    lowMid: 5,
    mid: -3,
    highMid: -1.5,
    treble: -2,
    widen: 0.2,
    punch: 0.55,
  },
  {
    id: 'surround3d',
    label: '3D丽音',
    hint: '声场拉开，包围感更强',
    bass: 2.5,
    lowMid: -1,
    mid: 1.5,
    highMid: 4,
    treble: 3.5,
    widen: 1,
    punch: 0.35,
  },
  {
    id: 'vocal',
    label: '清澈人声',
    hint: '人声前移，背景略收',
    bass: -4,
    lowMid: -3,
    mid: 8,
    highMid: 5.5,
    treble: 2.5,
    widen: 0.15,
    punch: 0.2,
  },
  {
    id: 'rock',
    label: '摇滚',
    hint: '鼓组与吉他更冲',
    bass: 8,
    lowMid: 3.5,
    mid: 2,
    highMid: 6,
    treble: 4,
    widen: 0.45,
    punch: 0.65,
  },
  {
    id: 'electronic',
    label: '电子激昂',
    hint: '更满、更响，接近短视频感',
    bass: 10,
    lowMid: 2,
    mid: -2,
    highMid: 7,
    treble: 6,
    widen: 0.6,
    punch: 0.85,
  },
  {
    id: 'soft',
    label: '柔和',
    hint: '压掉刺耳高频，更松软',
    bass: 2.5,
    lowMid: 1.5,
    mid: 0.5,
    highMid: -5,
    treble: -7,
    widen: 0.05,
    punch: 0.08,
  },
  {
    id: 'bright',
    label: '通透明亮',
    hint: '高频大幅提亮，细节更扎耳',
    bass: -2.5,
    lowMid: -2,
    mid: 1.5,
    highMid: 7,
    treble: 9,
    widen: 0.35,
    punch: 0.25,
  },
]

const STORAGE_KEY = 'kiftd.audioFx.preset'

export function loadAudioFxPresetId(): AudioFxPresetId {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (AUDIO_FX_PRESETS.some((p) => p.id === raw)) return raw as AudioFxPresetId
  } catch {
    /* ignore */
  }
  return 'hifi'
}

export function saveAudioFxPresetId(id: AudioFxPresetId) {
  try {
    localStorage.setItem(STORAGE_KEY, id)
  } catch {
    /* ignore */
  }
}

export function getAudioFxPreset(id: AudioFxPresetId): AudioFxPreset {
  return AUDIO_FX_PRESETS.find((p) => p.id === id) || AUDIO_FX_PRESETS[0]
}

type FxChain = {
  ctx: AudioContext
  source: MediaElementAudioSourceNode | null
  bass: BiquadFilterNode
  lowMid: BiquadFilterNode
  mid: BiquadFilterNode
  highMid: BiquadFilterNode
  treble: BiquadFilterNode
  dryGain: GainNode
  delay: DelayNode
  delayFilter: BiquadFilterNode
  delayGain: GainNode
  panL: StereoPannerNode
  panR: StereoPannerNode
  compressor: DynamicsCompressorNode
  makeUp: GainNode
  analyser: AnalyserNode
  outGain: GainNode
  element: HTMLMediaElement | null
}

let chain: FxChain | null = null
const sourcedElements = new WeakSet<HTMLMediaElement>()

function createChain(ctx: AudioContext): FxChain {
  const bass = ctx.createBiquadFilter()
  bass.type = 'lowshelf'
  bass.frequency.value = 90

  const lowMid = ctx.createBiquadFilter()
  lowMid.type = 'peaking'
  lowMid.frequency.value = 280
  lowMid.Q.value = 0.85

  const mid = ctx.createBiquadFilter()
  mid.type = 'peaking'
  mid.frequency.value = 1100
  mid.Q.value = 0.85

  const highMid = ctx.createBiquadFilter()
  highMid.type = 'peaking'
  highMid.frequency.value = 3500
  highMid.Q.value = 0.85

  const treble = ctx.createBiquadFilter()
  treble.type = 'highshelf'
  treble.frequency.value = 7000

  const dryGain = ctx.createGain()
  const delay = ctx.createDelay(0.08)
  delay.delayTime.value = 0.022
  const delayFilter = ctx.createBiquadFilter()
  delayFilter.type = 'highpass'
  delayFilter.frequency.value = 160
  const delayGain = ctx.createGain()
  const panL = ctx.createStereoPanner()
  panL.pan.value = -1
  const panR = ctx.createStereoPanner()
  panR.pan.value = 1

  const compressor = ctx.createDynamicsCompressor()
  compressor.threshold.value = -24
  compressor.knee.value = 12
  compressor.ratio.value = 1.2
  compressor.attack.value = 0.008
  compressor.release.value = 0.18

  const makeUp = ctx.createGain()
  const analyser = ctx.createAnalyser()
  analyser.fftSize = 256
  analyser.smoothingTimeConstant = 0.82
  const outGain = ctx.createGain()
  outGain.gain.value = 1

  bass.connect(lowMid)
  lowMid.connect(mid)
  mid.connect(highMid)
  highMid.connect(treble)

  treble.connect(dryGain)
  dryGain.connect(compressor)

  treble.connect(delay)
  delay.connect(delayFilter)
  delayFilter.connect(delayGain)
  delayGain.connect(panL)
  delayGain.connect(panR)
  panL.connect(compressor)
  panR.connect(compressor)

  compressor.connect(makeUp)
  makeUp.connect(analyser)
  analyser.connect(outGain)
  outGain.connect(ctx.destination)

  return {
    ctx,
    source: null,
    bass,
    lowMid,
    mid,
    highMid,
    treble,
    dryGain,
    delay,
    delayFilter,
    delayGain,
    panL,
    panR,
    compressor,
    makeUp,
    analyser,
    outGain,
    element: null,
  }
}

function attachSource(el: HTMLMediaElement) {
  if (!chain) return
  if (chain.element === el && chain.source) return

  if (chain.source) {
    try {
      chain.source.disconnect()
    } catch {
      /* ignore */
    }
    chain.source = null
  }

  if (sourcedElements.has(el)) {
    // 该元素已在别的 context 建过 source，无法再建；跳过 FX
    chain.element = el
    return
  }

  try {
    const source = chain.ctx.createMediaElementSource(el)
    source.connect(chain.bass)
    chain.source = source
    chain.element = el
    sourcedElements.add(el)
  } catch {
    chain.element = el
  }
}

export async function connectAudioElement(el: HTMLMediaElement | null | undefined) {
  if (!el) return
  const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
  if (!Ctx) return

  if (!chain || chain.ctx.state === 'closed') {
    chain = createChain(new Ctx())
    applyAudioFxPreset(loadAudioFxPresetId())
  }

  attachSource(el)
  if (chain.ctx.state === 'suspended') {
    await chain.ctx.resume().catch(() => undefined)
  }
}

export function disconnectAudioFx() {
  if (!chain) return
  const c = chain
  chain = null
  try {
    c.source?.disconnect()
  } catch {
    /* ignore */
  }
  try {
    void c.ctx.close()
  } catch {
    /* ignore */
  }
}

export function applyAudioFxPreset(id: AudioFxPresetId) {
  if (!chain) {
    saveAudioFxPresetId(id)
    return
  }
  const p = getAudioFxPreset(id)
  const g = chain
  const now = g.ctx.currentTime

  g.bass.gain.setTargetAtTime(p.bass, now, 0.02)
  g.lowMid.gain.setTargetAtTime(p.lowMid, now, 0.02)
  g.mid.gain.setTargetAtTime(p.mid, now, 0.02)
  g.highMid.gain.setTargetAtTime(p.highMid, now, 0.02)
  g.treble.gain.setTargetAtTime(p.treble, now, 0.02)

  const widen = Math.min(1, Math.max(0, p.widen))
  // 加大 wet 比例，3D/开阔类预设听感更明显
  const wet = widen * 0.95
  const dry = Math.max(0.35, 1 - wet * 0.55)
  g.dryGain.gain.setTargetAtTime(dry, now, 0.02)
  g.delayGain.gain.setTargetAtTime(wet, now, 0.02)
  g.delay.delayTime.setTargetAtTime(0.012 + widen * 0.028, now, 0.02)
  g.panL.pan.setTargetAtTime(-0.55 - widen * 0.45, now, 0.02)
  g.panR.pan.setTargetAtTime(0.55 + widen * 0.45, now, 0.02)

  const punch = Math.min(1, Math.max(0, p.punch))
  g.compressor.ratio.setTargetAtTime(1.15 + punch * 7.5, now, 0.04)
  g.compressor.threshold.setTargetAtTime(-16 - punch * 16, now, 0.04)
  g.compressor.knee.setTargetAtTime(18 - punch * 10, now, 0.04)
  g.makeUp.gain.setTargetAtTime(1 + punch * 0.35, now, 0.04)

  // 处理后略提输出，避免“听起来变小”掩盖效果差异；HiFi 保持中性
  const loudnessBoost =
    p.id === 'hifi' ? 1 : p.id === 'soft' ? 1.05 : p.id === 'electronic' || p.id === 'bass' ? 1.12 : 1.08
  g.outGain.gain.setTargetAtTime(loudnessBoost, now, 0.03)

  saveAudioFxPresetId(id)
}

export function getAudioAnalyser(): AnalyserNode | null {
  return chain?.analyser ?? null
}

export async function resumeAudioFx() {
  if (!chain) return
  if (chain.ctx.state === 'suspended') await chain.ctx.resume().catch(() => undefined)
}

export function sampleAudioSpectrum(bars: number): number[] {
  const analyser = chain?.analyser
  if (!analyser || bars <= 0) return Array.from({ length: bars }, () => 0)
  const data = new Uint8Array(analyser.frequencyBinCount)
  analyser.getByteFrequencyData(data)
  const out: number[] = []
  const slice = Math.max(1, Math.floor(data.length / bars))
  for (let i = 0; i < bars; i++) {
    let sum = 0
    const start = i * slice
    for (let j = 0; j < slice && start + j < data.length; j++) sum += data[start + j]
    out.push(Math.min(1, sum / (slice * 255)))
  }
  return out
}
