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
  404: '没有找到该任务。',
  409: '任务正在执行，请等待当前回复完成。',
  502: '模型服务调用失败，请稍后重试。',
  503: '模型服务尚未配置，请检查后端环境变量。',
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response

  try {
    response = await fetch(`/api${path}`, options)
  } catch {
    throw new Error('无法连接后端服务，请确认服务已启动。')
  }

  if (!response.ok) {
    throw new ApiError(response.status, errorMessages[response.status] ?? '请求失败，请稍后重试。')
  }

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
