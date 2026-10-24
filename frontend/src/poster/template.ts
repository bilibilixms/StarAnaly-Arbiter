export interface PosterInput {
  title: string
  description: string
  direction: string
  sourceImage?: string
  count: number
  revision: number
}

const palettes = [
  { sky: '#d7edff', blush: '#ffedf6', ink: '#244b6b', accent: '#d85c96' },
  { sky: '#e8e1ff', blush: '#fff5f7', ink: '#40365f', accent: '#6a8ed0' },
  { sky: '#c8e7ed', blush: '#f4e4f5', ink: '#255167', accent: '#b26daf' },
  { sky: '#ebefff', blush: '#fcece6', ink: '#374b76', accent: '#e080a6' },
]

function wrap(context: CanvasRenderingContext2D, text: string, width: number, limit: number): string[] {
  const lines: string[] = []
  let line = ''
  for (const char of Array.from(text)) {
    if (line && context.measureText(line + char).width > width) {
      lines.push(line)
      line = char
      if (lines.length === limit) break
    } else line += char
  }
  if (line && lines.length < limit) lines.push(line)
  return lines
}

async function sourceBitmap(url?: string): Promise<ImageBitmap | null> {
  if (!url) return null
  const response = await fetch(url)
  if (!response.ok) throw new Error('无法读取需求图片。')
  return createImageBitmap(await response.blob())
}

export async function makeTemplatePosters(input: PosterInput): Promise<File[]> {
  const bitmap = await sourceBitmap(input.sourceImage)
  try {
    const files: File[] = []
    for (let index = 0; index < input.count; index++) {
      const canvas = document.createElement('canvas')
      canvas.width = 1080
      canvas.height = 1350
      const ctx = canvas.getContext('2d')
      if (!ctx) throw new Error('浏览器无法绘制海报。')
      const palette = palettes[(index + input.revision - 1) % palettes.length]!
      const gradient = ctx.createLinearGradient(0, 0, 1080, 1350)
      gradient.addColorStop(0, palette.sky)
      gradient.addColorStop(1, palette.blush)
      ctx.fillStyle = gradient
      ctx.fillRect(0, 0, 1080, 1350)
      ctx.fillStyle = 'rgba(255,255,255,.76)'
      ctx.fillRect(48, 48, 984, 1254)
      ctx.fillStyle = palette.accent
      ctx.fillRect(92, 102, index % 2 ? 130 : 78, 8)
      ctx.fillStyle = palette.ink
      ctx.font = '600 31px "PingFang SC", "Microsoft YaHei", sans-serif'
      ctx.fillText('星析 · 宣传提案', 92, 168)
      ctx.font = '700 76px "PingFang SC", "Microsoft YaHei", sans-serif'
      const headline = input.direction.trim() || input.title
      wrap(ctx, headline.slice(0, 90), 880, 3).forEach((line, lineIndex) => ctx.fillText(line, 92, 280 + lineIndex * 92))
      if (bitmap) {
        const imageY = 490
        const imageHeight = 565
        const ratio = Math.max(900 / bitmap.width, imageHeight / bitmap.height)
        const width = bitmap.width * ratio
        const height = bitmap.height * ratio
        ctx.save()
        ctx.beginPath()
        ctx.roundRect(90, imageY, 900, imageHeight, 28)
        ctx.clip()
        ctx.drawImage(bitmap, 90 + (900 - width) / 2, imageY + (imageHeight - height) / 2, width, height)
        ctx.restore()
      } else {
        ctx.fillStyle = palette.sky
        ctx.beginPath()
        ctx.arc(540, 770, 235, 0, Math.PI * 2)
        ctx.fill()
        ctx.fillStyle = palette.blush
        ctx.beginPath()
        ctx.arc(660, 700, 145, 0, Math.PI * 2)
        ctx.fill()
      }
      ctx.fillStyle = palette.ink
      ctx.font = '400 36px "PingFang SC", "Microsoft YaHei", sans-serif'
      const description = input.description.trim() || input.title
      wrap(ctx, description.slice(0, 180), 900, 3).forEach((line, lineIndex) => ctx.fillText(line, 92, 1134 + lineIndex * 53))
      const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(
        (result) => result ? resolve(result) : reject(new Error('海报导出失败。')), 'image/jpeg', .88))
      files.push(new File([blob], `poster-${input.revision}-${index + 1}.jpg`, { type: 'image/jpeg' }))
    }
    return files
  } finally { bitmap?.close() }
}
