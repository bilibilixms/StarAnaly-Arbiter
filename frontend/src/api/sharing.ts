export interface XiaohongshuDraft {
  title: string
  body: string
  hashtags: string[]
}

export async function generateXiaohongshuDraft(image: File, requirements: string): Promise<XiaohongshuDraft> {
  const form = new FormData()
  form.append('image', image)
  form.append('requirements', requirements)

  let response: Response
  try {
    response = await fetch('/api/share/xiaohongshu/generate', { method: 'POST', body: form })
  } catch {
    throw new Error('无法连接后端服务，请确认服务已启动。')
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { detail?: unknown } | null
    throw new Error(typeof problem?.detail === 'string' ? problem.detail : '生成失败，请稍后重试。')
  }
  return await response.json() as XiaohongshuDraft
}
