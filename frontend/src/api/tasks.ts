export interface Task {
  id: number
  title: string
  description: string | null
  status: string
  createdAt: string
  lastError: string | null
}

export interface TaskMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}

export interface TaskRun {
  id: number
  taskId: number
  status: string
  requestSummary: string
  lastError: string | null
  startedAt: string
  finishedAt: string | null
}

export interface TaskRunPage {
  content: TaskRun[]
  totalElements: number
  totalPages: number
}

export interface TaskRunStep {
  id: number
  stepNumber: number
  kind: string
  status: string
  toolName: string | null
  inputSummary: string
  outputSummary: string | null
  startedAt: string
  finishedAt: string | null
}

export interface CsvFile {
  id: number
  taskId: number
  name: string
  byteSize: number
  rowCount: number
  headers: string[]
  createdAt: string
}

export interface CsvPreview {
  file: CsvFile
  rows: Record<string, string>[]
}

export interface CsvAggregateGroup {
  key: string
  sum: number
  rowCount: number
}

export interface CsvAggregate {
  fileId: number
  groupBy: string
  sumColumn: string
  byMonth: boolean
  matchedRows: number
  total: number
  groups: CsvAggregateGroup[]
}

export interface CsvAnalysis {
  id: number
  runId: number
  result: CsvAggregate
  createdAt: string
}

export interface KnowledgeDocument {
  id: number
  taskId: number
  name: string
  byteSize: number
  chunkCount: number
  createdAt: string
}

export interface KnowledgeCitation {
  id: number
  documentId: number
  documentName: string
  startLine: number
  endLine: number
  excerpt: string
}

export interface TaskPage {
  content: Task[]
  number: number
  totalElements: number
  totalPages: number
}

export interface CreateTaskInput {
  title: string
  description?: string | null
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const errorMessages: Record<number, string> = {
  400: '提交的内容不符合要求，请检查后重试。',
  404: '没有找到请求的任务或资料。',
  409: '任务正在执行，请等待当前回复完成。',
  413: '上传文件超过大小限制。',
  502: '无法连接后端服务，请确认服务已启动并重试。',
  503: '服务暂时不可用，请稍后重试。',
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response

  try {
    response = await fetch(`/api${path}`, options)
  } catch {
    throw new Error('无法连接后端服务，请确认服务已启动。')
  }

  if (!response.ok) {
    // 后端的 ProblemDetail 会说明模型错误；代理返回的 502 通常没有该字段。
    const problem = await response.json().catch(() => null) as { detail?: unknown } | null
    const detail = typeof problem?.detail === 'string' ? problem.detail : null
    throw new ApiError(response.status, detail || errorMessages[response.status] || '请求失败，请稍后重试。')
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export function listTasks(page = 0): Promise<TaskPage> {
  return request<TaskPage>(`/tasks?page=${page}&size=20`)
}

export function getTask(id: number): Promise<Task> {
  return request<Task>(`/tasks/${id}`)
}

export function createTask(input: CreateTaskInput): Promise<Task> {
  return request<Task>('/tasks', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function listTaskMessages(taskId: number): Promise<TaskMessage[]> {
  return request<TaskMessage[]>(`/tasks/${taskId}/messages`)
}

export function sendTaskMessage(taskId: number, content: string): Promise<TaskMessage> {
  return request<TaskMessage>(`/tasks/${taskId}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
  })
}

export function startTaskRun(taskId: number, content: string): Promise<TaskRun> {
  return request<TaskRun>(`/tasks/${taskId}/runs`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
  })
}

export function listTaskRuns(taskId: number): Promise<TaskRunPage> {
  return request<TaskRunPage>(`/tasks/${taskId}/runs?page=0&size=20`)
}

export function listRunSteps(taskId: number, runId: number): Promise<TaskRunStep[]> {
  return request<TaskRunStep[]>(`/tasks/${taskId}/runs/${runId}/steps`)
}

export function listRunAnalyses(taskId: number, runId: number): Promise<CsvAnalysis[]> {
  return request<CsvAnalysis[]>(`/tasks/${taskId}/runs/${runId}/analysis`)
}

export function listRunCitations(taskId: number, runId: number): Promise<KnowledgeCitation[]> {
  return request<KnowledgeCitation[]>(`/tasks/${taskId}/runs/${runId}/citations`)
}

export function listCsvFiles(taskId: number): Promise<CsvFile[]> {
  return request<CsvFile[]>(`/tasks/${taskId}/files`)
}

export function getCsvPreview(taskId: number, fileId: number): Promise<CsvPreview> {
  return request<CsvPreview>(`/tasks/${taskId}/files/${fileId}`)
}

function uploadFile<T>(path: string, file: File): Promise<T> {
  const body = new FormData()
  body.append('file', file)
  return request<T>(path, { method: 'POST', body })
}

export function uploadCsvFile(taskId: number, file: File): Promise<CsvFile> {
  return uploadFile<CsvFile>(`/tasks/${taskId}/files`, file)
}

export function listKnowledgeDocuments(taskId: number): Promise<KnowledgeDocument[]> {
  return request<KnowledgeDocument[]>(`/tasks/${taskId}/documents`)
}

export function uploadKnowledgeDocument(taskId: number, file: File): Promise<KnowledgeDocument> {
  return uploadFile<KnowledgeDocument>(`/tasks/${taskId}/documents`, file)
}

export function deleteKnowledgeDocument(taskId: number, documentId: number): Promise<void> {
  return request<void>(`/tasks/${taskId}/documents/${documentId}`, { method: 'DELETE' })
}
