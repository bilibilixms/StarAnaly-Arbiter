export interface XiaohongshuDraft {
  title: string
  body: string
  hashtags: string[]
}

export interface XiaohongshuNote {
  id: number
  status: 'DRAFT' | 'PUBLISHED'
  requirements: string
  title: string
  body: string
  tagsText: string
  imageName: string
  imageSize: number
  createdAt: string
  updatedAt: string
  publishedAt: string | null
}

const notesUrl = '/api/share/xiaohongshu/notes'

async function readResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { detail?: unknown } | null
    throw new Error(typeof problem?.detail === 'string' ? problem.detail : '操作失败，请稍后重试。')
  }
  return await response.json() as T
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  try {
    return await readResponse<T>(await fetch(url, init))
  } catch (cause) {
    if (cause instanceof TypeError) throw new Error('无法连接后端服务，请确认服务已启动。')
    throw cause
  }
}

export function listXiaohongshuNotes(status: XiaohongshuNote['status']): Promise<XiaohongshuNote[]> {
  return request(`${notesUrl}?status=${status}`)
}

export async function loadXiaohongshuImage(note: XiaohongshuNote): Promise<File> {
  let response: Response
  try {
    response = await fetch(`${notesUrl}/${note.id}/image`)
  } catch {
    throw new Error('无法连接后端服务，请确认服务已启动。')
  }
  if (!response.ok) throw new Error('读取保存的图片失败。')
  return new File([await response.blob()], note.imageName, {
    type: response.headers.get('Content-Type') || 'application/octet-stream',
  })
}

export function saveXiaohongshuNote(fields: {
  image?: File | null
  requirements: string
  title: string
  body: string
  tagsText: string
}, id?: number): Promise<XiaohongshuNote> {
  const form = new FormData()
  if (fields.image) form.append('image', fields.image)
  form.append('requirements', fields.requirements)
  form.append('title', fields.title)
  form.append('body', fields.body)
  form.append('tagsText', fields.tagsText)
  return request(id ? `${notesUrl}/${id}` : notesUrl, { method: id ? 'PUT' : 'POST', body: form })
}

export function markXiaohongshuNotePublished(id: number): Promise<XiaohongshuNote> {
  return request(`${notesUrl}/${id}/published`, { method: 'POST' })
}

export async function deleteXiaohongshuNote(id: number): Promise<void> {
  let response: Response
  try {
    response = await fetch(`${notesUrl}/${id}`, { method: 'DELETE' })
  } catch {
    throw new Error('无法连接后端服务，请确认服务已启动。')
  }
  if (!response.ok) await readResponse(response)
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
