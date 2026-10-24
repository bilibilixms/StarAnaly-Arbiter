interface GeneratedPoster { name: string; mime: string; base64: string }

export async function getPosterStatus(): Promise<boolean> {
  const response = await fetch('/api/posters/status')
  if (!response.ok) throw new Error('无法读取图像服务状态。')
  const status = await response.json() as { configured: boolean }
  return status.configured
}

export async function generateAiPosters(fields: {
  taskId: number
  direction: string
  advice: string
  count: number
  referenceImage?: File
}): Promise<File[]> {
  const form = new FormData()
  form.append('taskId', String(fields.taskId))
  form.append('direction', fields.direction)
  form.append('advice', fields.advice)
  form.append('count', String(fields.count))
  if (fields.referenceImage) form.append('referenceImage', fields.referenceImage)
  const response = await fetch('/api/posters/generate', { method: 'POST', body: form })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { detail?: unknown } | null
    throw new Error(typeof problem?.detail === 'string' ? problem.detail : '图像生成失败，请稍后重试。')
  }
  const result = await response.json() as GeneratedPoster[]
  return Promise.all(result.map(async (item) => {
    const blob = await (await fetch(`data:${item.mime};base64,${item.base64}`)).blob()
    return new File([blob], item.name, { type: item.mime })
  }))
}
