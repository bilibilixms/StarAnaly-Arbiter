export function formatCreatedAt(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function statusLabel(value: string): string {
  const labels: Record<string, string> = {
    PENDING: '等待处理',
    RUNNING: '执行中',
    SUCCEEDED: '已完成',
    FAILED: '执行失败',
  }
  return labels[value] ?? value
}
