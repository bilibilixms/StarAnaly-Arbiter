const databaseName = 'staranaly-poster-transfer'
const storeName = 'batches'

interface StoredBatch {
  id: string
  createdAt: number
  files: File[]
}

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(databaseName, 1)
    request.onupgradeneeded = () => request.result.createObjectStore(storeName, { keyPath: 'id' })
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(new Error('浏览器无法保存待发布海报。'))
  })
}

export async function savePosterTransfer(files: File[]): Promise<string> {
  if (files.length < 1 || files.length > 4) throw new Error('请选择 1 至 4 张海报。')
  const id = crypto.randomUUID()
  const database = await openDatabase()
  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = database.transaction(storeName, 'readwrite')
      transaction.objectStore(storeName).put({ id, files, createdAt: Date.now() } satisfies StoredBatch)
      transaction.oncomplete = () => resolve()
      transaction.onerror = () => reject(new Error('保存待发布海报失败。'))
    })
  } finally { database.close() }
  return id
}

export async function loadPosterTransfer(id: string): Promise<File[]> {
  const database = await openDatabase()
  try {
    const batch = await new Promise<StoredBatch | undefined>((resolve, reject) => {
      const transaction = database.transaction(storeName, 'readonly')
      const request = transaction.objectStore(storeName).get(id)
      request.onsuccess = () => resolve(request.result as StoredBatch | undefined)
      request.onerror = () => reject(new Error('读取海报失败。'))
    })
    if (!batch || Date.now() - batch.createdAt > 24 * 60 * 60 * 1000) {
      throw new Error('待发布海报已失效，请返回工作台重新生成。')
    }
    return batch.files
  } finally { database.close() }
}

export async function removePosterTransfer(id: string): Promise<void> {
  const database = await openDatabase()
  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = database.transaction(storeName, 'readwrite')
      transaction.objectStore(storeName).delete(id)
      transaction.oncomplete = () => resolve()
      transaction.onerror = () => reject(new Error('清理临时海报失败。'))
    })
  } finally { database.close() }
}
