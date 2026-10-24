export interface DouyinDraft {
  title: string
  body: string
  hashtags: string[]
}

export interface DouyinNote {
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
  taskId: number | null
  directionIndex: number | null
}

export interface DouyinMaterial {
  id: number
  name: string
  content: string
  sourceName: string | null
  createdAt: string
  updatedAt: string
}

const notesUrl = '/api/share/douyin/notes'
const materialsUrl = '/api/share/douyin/materials'

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

export function listDouyinNotes(status: DouyinNote['status']): Promise<DouyinNote[]> {
  return request(`${notesUrl}?status=${status}`)
}

export function listTaskDouyinNotes(taskId: number): Promise<DouyinNote[]> {
  return request(`/api/share/douyin/tasks/${taskId}/notes`)
}

export function getDouyinNote(id: number): Promise<DouyinNote> {
  return request(`${notesUrl}/${id}`)
}

export async function loadDouyinImage(note: DouyinNote): Promise<File> {
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

export async function loadDouyinImages(note: DouyinNote): Promise<File[]> {
  const response = await fetch(`${notesUrl}/${note.id}/images`)
  if (!response.ok) throw new Error('读取保存的图片列表失败。')
  const items = await response.json() as Array<{ id: number; name: string; mime: string }>
  return Promise.all(items.map(async (item) => {
    const path = item.id === 0 ? `${notesUrl}/${note.id}/image` : `${notesUrl}/${note.id}/images/${item.id}`
    const imageResponse = await fetch(path)
    if (!imageResponse.ok) throw new Error('读取保存的图片失败。')
    return new File([await imageResponse.blob()], item.name, { type: item.mime })
  }))
}

export function saveDouyinNote(fields: {
  image?: File | null
  extraImages?: File[]
  requirements: string
  title: string
  body: string
  tagsText: string
  taskId?: number | null
  directionIndex?: number | null
}, id?: number): Promise<DouyinNote> {
  const form = new FormData()
  if (fields.image) form.append('image', fields.image)
  if (fields.extraImages) for (const image of fields.extraImages) form.append('extraImages', image)
  form.append('requirements', fields.requirements)
  form.append('title', fields.title)
  form.append('body', fields.body)
  form.append('tagsText', fields.tagsText)
  if (fields.taskId) form.append('taskId', String(fields.taskId))
  if (fields.directionIndex !== null && fields.directionIndex !== undefined) form.append('directionIndex', String(fields.directionIndex))
  return request(id ? `${notesUrl}/${id}` : notesUrl, { method: id ? 'PUT' : 'POST', body: form })
}

export function markDouyinNotePublished(id: number): Promise<DouyinNote> {
  return request(`${notesUrl}/${id}/published`, { method: 'POST' })
}

export async function deleteDouyinNote(id: number): Promise<void> {
  let response: Response
  try {
    response = await fetch(`${notesUrl}/${id}`, { method: 'DELETE' })
  } catch {
    throw new Error('无法连接后端服务，请确认服务已启动。')
  }
  if (!response.ok) await readResponse(response)
}

export function listDouyinMaterials(): Promise<DouyinMaterial[]> {
  return request(materialsUrl)
}

export function saveDouyinMaterial(fields: { name: string; content?: string; file?: File | null }, id?: number): Promise<DouyinMaterial> {
  const form = new FormData()
  form.append('name', fields.name)
  if (fields.file) form.append('file', fields.file)
  else if (fields.content !== undefined) form.append('content', fields.content)
  return request(id ? `${materialsUrl}/${id}` : materialsUrl, { method: id ? 'PUT' : 'POST', body: form })
}

export async function deleteDouyinMaterial(id: number): Promise<void> {
  let response: Response
  try {
    response = await fetch(`${materialsUrl}/${id}`, { method: 'DELETE' })
  } catch {
    throw new Error('无法连接后端服务，请确认服务已启动。')
  }
  if (!response.ok) await readResponse(response)
}

export async function generateDouyinDraft(image: File, requirements: string, materialIds: number[] = [],
  extraImages: File[] = [],
  taskId?: number | null, direction?: number | null): Promise<DouyinDraft> {
  const form = new FormData()
  form.append('image', image)
  for (const extra of extraImages) form.append('extraImages', extra)
  form.append('requirements', requirements)
  for (const id of materialIds) form.append('materialIds', String(id))
  if (taskId) form.append('taskId', String(taskId))
  if (direction !== null && direction !== undefined) form.append('direction', String(direction))

  let response: Response
  try {
    response = await fetch('/api/share/douyin/generate', { method: 'POST', body: form })
  } catch {
    throw new Error('无法连接后端服务，请确认服务已启动。')
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { detail?: unknown } | null
    throw new Error(typeof problem?.detail === 'string' ? problem.detail : '生成失败，请稍后重试。')
  }
  return await response.json() as DouyinDraft
}
