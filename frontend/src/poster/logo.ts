import { posterImageUrl } from '@/api/posters'

async function bitmap(url: string): Promise<ImageBitmap> {
  const response = await fetch(url)
  if (!response.ok) throw new Error('无法读取原始 Logo 或海报。')
  return createImageBitmap(await response.blob())
}

export async function overlayOriginalLogos(taskId: number, jobId: number, imageId: number,
  logoIds: number[]): Promise<File> {
  if (!logoIds.length) throw new Error('本版未选用 Logo 素材。')
  const poster = await bitmap(posterImageUrl(taskId, jobId, imageId))
  const logos: ImageBitmap[] = []
  try {
    for (const id of logoIds) logos.push(await bitmap(`/api/posters/tasks/${taskId}/jobs/${jobId}/logos/${id}`))
    const canvas = document.createElement('canvas')
    canvas.width = poster.width
    canvas.height = poster.height
    const context = canvas.getContext('2d')
    if (!context) throw new Error('当前浏览器无法合成 Logo。')
    context.drawImage(poster, 0, 0)
    const margin = Math.round(canvas.width * 0.04)
    const slotWidth = Math.min(canvas.width * 0.25, (canvas.width - margin * (logos.length + 1)) / logos.length)
    const maxHeight = canvas.height * 0.12
    logos.forEach((logo, index) => {
      const scale = Math.min(slotWidth / logo.width, maxHeight / logo.height)
      const width = Math.round(logo.width * scale)
      const height = Math.round(logo.height * scale)
      const x = margin + index * (slotWidth + margin)
      context.fillStyle = 'rgba(255, 255, 255, 0.92)'
      context.fillRect(x - 8, margin - 8, width + 16, height + 16)
      context.drawImage(logo, x, margin, width, height)
    })
    const result = await new Promise<Blob>((resolve, reject) => canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error('无法导出叠加 Logo 后的图片。'))
    }, 'image/png'))
    return new File([result], `poster-${jobId}-${imageId}-logo.png`, { type: 'image/png' })
  } finally {
    poster.close()
    logos.forEach((logo) => logo.close())
  }
}
