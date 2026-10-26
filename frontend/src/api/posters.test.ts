import assert from 'node:assert/strict'
import test from 'node:test'
import { listPosterJobs, savePosterReview, savePosterSelection, submitPosterJob } from './posters.ts'

test('后台生成请求带固定标识，刷新后从服务端恢复同一版本', async () => {
  const previous = globalThis.fetch
  const requestKey = '20e902fa-f132-4e03-bedd-14f40f186af7'
  let submitted = false
  globalThis.fetch = async (input, init) => {
    if (String(input).endsWith('/jobs') && init?.method === 'POST') {
      assert.equal((init.body as FormData).get('requestKey'), requestKey)
      assert.equal((init.body as FormData).get('requiredText'), '活动日期')
      submitted = true
      return Response.json({ id: 17, status: 'PENDING', images: [] })
    }
    assert.equal(String(input), '/api/posters/tasks/3/jobs')
    return Response.json([{ id: 17, status: 'SUCCEEDED', selected: [0], images: [{ id: 5, position: 0 }] }])
  }
  try {
    await submitPosterJob({ taskId: 3, requestKey, direction: '活动海报', advice: '', count: 1,
      requiredText: '活动日期', forbiddenContent: '', materialIds: [] })
    const history = await listPosterJobs(3)
    assert.equal(submitted, true)
    assert.equal(history[0]?.id, 17)
    assert.deepEqual(history[0]?.selected, [0])
  } finally { globalThis.fetch = previous }
})

test('海报选择和逐张核对结果保存到对应任务与图片', async () => {
  const previous = globalThis.fetch
  const calls: Array<{ url: string; body: string }> = []
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), body: String(init?.body) })
    return new Response(null, { status: 204 })
  }
  try {
    await savePosterSelection(3, 17, [0, 2])
    await savePosterReview(3, 17, 5, { requiredText: true, forbiddenContent: true, logo: false })
    assert.deepEqual(calls, [
      { url: '/api/posters/tasks/3/jobs/17/selection', body: '[0,2]' },
      { url: '/api/posters/tasks/3/jobs/17/images/5/review',
        body: '{"requiredText":true,"forbiddenContent":true,"logo":false}' },
    ])
  } finally { globalThis.fetch = previous }
})
