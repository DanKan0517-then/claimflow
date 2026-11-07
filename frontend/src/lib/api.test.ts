import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, onServerWaking } from './api'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('api cold-start handling', () => {
  it('retries 502s while the server wakes, then succeeds', async () => {
    vi.useFakeTimers()
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response('bad gateway', { status: 502 }))
      .mockResolvedValueOnce(new Response('bad gateway', { status: 502 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const states: boolean[] = []
    const off = onServerWaking((w) => states.push(w))

    const p = api.get<{ ok: boolean }>('/ping')
    await vi.advanceTimersByTimeAsync(7000)
    await expect(p).resolves.toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(states).toEqual([true, false])
    off()
  })

  it('does not retry real errors', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ detail: 'Claim not found' }), { status: 404, headers: { 'Content-Type': 'application/json' } }),
    )
    vi.stubGlobal('fetch', fetchMock)
    await expect(api.get('/claims/9')).rejects.toThrow('Claim not found')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
