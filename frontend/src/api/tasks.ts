export interface Task {
  id: number
  title: string
  description: string | null
  status: string
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

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response

  try {
    response = await fetch(`/api${path}`, options)
  } catch {
    throw new Error('无法连接后端服务，请确认服务已启动。')
  }

  if (!response.ok) {
    const message =
      response.status === 400
        ? '提交的内容不符合要求，请检查后重试。'
        : response.status === 404
          ? '没有找到该任务。'
          : '请求失败，请稍后重试。'
    throw new ApiError(response.status, message)
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
