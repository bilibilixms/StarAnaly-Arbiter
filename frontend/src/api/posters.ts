export interface PosterJobImage {
  id: number
  position: number
  name: string
  mime: string
  logoApplied: boolean
  reviewJson: string
}

export interface PosterJob {
  id: number
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
  direction: string
  advice: string
  materialSnapshot: string
  checksJson: string
  error: string | null
  selected: number[]
  images: PosterJobImage[]
  requestedCount: number
  createdAt: string
}

export interface PosterChecks { requiredText: string; forbiddenContent: string; logoName: string; logoIds: number[] }
export interface PosterReview { requiredText: boolean; forbiddenContent: boolean; logo: boolean }

export function posterImageUrl(taskId: number, jobId: number, imageId: number): string {
  return `/api/posters/tasks/${taskId}/jobs/${jobId}/images/${imageId}`
}

export async function listPosterJobs(taskId: number): Promise<PosterJob[]> {
  const response = await fetch(`/api/posters/tasks/${taskId}/jobs`)
  if (!response.ok) throw await posterError(response, '无法读取海报版本。')
  return response.json() as Promise<PosterJob[]>
}

export async function submitPosterJob(fields: {
  taskId: number; requestKey: string; direction: string; advice: string; count: number
  requiredText: string; forbiddenContent: string; referenceImage?: File; materialIds: number[]
}): Promise<PosterJob> {
  const form = new FormData()
  form.append('requestKey', fields.requestKey)
  form.append('direction', fields.direction)
  form.append('advice', fields.advice)
  form.append('count', String(fields.count))
  form.append('requiredText', fields.requiredText)
  form.append('forbiddenContent', fields.forbiddenContent)
  if (fields.referenceImage) form.append('referenceImage', fields.referenceImage)
  for (const id of fields.materialIds) form.append('materialIds', String(id))
  const response = await fetch(`/api/posters/tasks/${fields.taskId}/jobs`, { method: 'POST', body: form })
  if (!response.ok) throw await posterError(response, '无法提交生成任务。')
  return response.json() as Promise<PosterJob>
}

export async function importPosterJob(fields: {
  taskId: number; requestKey: string; direction: string; advice: string; files: File[]
  requiredText: string; forbiddenContent: string; materialIds: number[]
}): Promise<PosterJob> {
  const form = new FormData()
  form.append('requestKey', fields.requestKey)
  form.append('direction', fields.direction)
  form.append('advice', fields.advice)
  form.append('requiredText', fields.requiredText)
  form.append('forbiddenContent', fields.forbiddenContent)
  for (const file of fields.files) form.append('files', file)
  for (const id of fields.materialIds) form.append('materialIds', String(id))
  const response = await fetch(`/api/posters/tasks/${fields.taskId}/jobs/import`, { method: 'POST', body: form })
  if (!response.ok) throw await posterError(response, '无法保存海报版本。')
  return response.json() as Promise<PosterJob>
}

export async function savePosterSelection(taskId: number, jobId: number, positions: number[]): Promise<void> {
  const response = await fetch(`/api/posters/tasks/${taskId}/jobs/${jobId}/selection`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(positions),
  })
  if (!response.ok) throw await posterError(response, '无法保存海报选择。')
}

export async function savePosterReview(taskId: number, jobId: number, imageId: number, review: PosterReview): Promise<void> {
  const response = await fetch(`/api/posters/tasks/${taskId}/jobs/${jobId}/images/${imageId}/review`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(review),
  })
  if (!response.ok) throw await posterError(response, '无法保存核对结果。')
}

export async function replacePosterImage(taskId: number, jobId: number, imageId: number, file: File): Promise<void> {
  const form = new FormData()
  form.append('file', file)
  const response = await fetch(posterImageUrl(taskId, jobId, imageId), { method: 'PUT', body: form })
  if (!response.ok) throw await posterError(response, '无法保存叠加 Logo 后的海报。')
}

export async function cancelPosterJob(taskId: number, jobId: number): Promise<void> {
  const response = await fetch(`/api/posters/tasks/${taskId}/jobs/${jobId}/cancel`, { method: 'POST' })
  if (!response.ok) throw await posterError(response, '无法取消生成任务。')
}

export async function deletePosterJob(taskId: number, jobId: number): Promise<void> {
  const response = await fetch(`/api/posters/tasks/${taskId}/jobs/${jobId}`, { method: 'DELETE' })
  if (!response.ok) throw await posterError(response, '无法删除海报版本。')
}

export interface PosterMaterial {
  id: number
  name: string
  type: 'TEXT' | 'LOGO' | 'REFERENCE_IMAGE'
  content: string
  sourceName: string | null
  mime: string | null
  updatedAt: string
}

async function posterError(response: Response, fallback: string): Promise<Error> {
  const problem = await response.json().catch(() => null) as { detail?: unknown } | null
  return new Error(typeof problem?.detail === 'string' ? problem.detail : fallback)
}

export async function listPosterMaterials(): Promise<PosterMaterial[]> {
  const response = await fetch('/api/posters/materials')
  if (!response.ok) throw await posterError(response, '无法读取海报素材。')
  return response.json() as Promise<PosterMaterial[]>
}

export async function savePosterMaterial(fields: {
  name: string
  type: PosterMaterial['type']
  content?: string
  file?: File | null
}, id?: number): Promise<PosterMaterial> {
  const form = new FormData()
  form.append('name', fields.name)
  if (!id) form.append('type', fields.type)
  if (fields.content !== undefined) form.append('content', fields.content)
  if (fields.file) form.append('file', fields.file)
  const response = await fetch(id ? `/api/posters/materials/${id}` : '/api/posters/materials', {
    method: id ? 'PUT' : 'POST', body: form,
  })
  if (!response.ok) throw await posterError(response, '保存海报素材失败。')
  return response.json() as Promise<PosterMaterial>
}

export async function deletePosterMaterial(id: number): Promise<void> {
  const response = await fetch(`/api/posters/materials/${id}`, { method: 'DELETE' })
  if (!response.ok) throw await posterError(response, '删除海报素材失败。')
}

export interface PosterStatus { configured: boolean; dailyLimit: number; usedToday: number }

export async function getPosterStatus(): Promise<PosterStatus> {
  const response = await fetch('/api/posters/status')
  if (!response.ok) throw new Error('无法读取图像服务状态。')
  return response.json() as Promise<PosterStatus>
}
