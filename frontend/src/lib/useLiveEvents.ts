import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { tokenStore } from './api'
import type { ClaimEvent } from './types'

/**
 * One WebSocket per tab. On any claim event we invalidate the cached queries that could be stale,
 * so open screens refresh themselves. Reconnects with backoff (e.g. while nginx restarts a replica).
 */
export function useLiveEvents(enabled: boolean, onEvent?: (e: ClaimEvent) => void) {
  const queryClient = useQueryClient()
  const [connected, setConnected] = useState(false)
  const handler = useRef(onEvent)
  useEffect(() => {
    handler.current = onEvent
  }, [onEvent])

  useEffect(() => {
    if (!enabled) return
    let socket: WebSocket | null = null
    let retry = 0
    let timer: ReturnType<typeof setTimeout> | undefined
    let ping: ReturnType<typeof setInterval> | undefined
    let stopped = false

    const connect = () => {
      const token = tokenStore.get()
      if (!token || stopped) return
      const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
      socket = new WebSocket(`${proto}://${window.location.host}/ws?token=${encodeURIComponent(token)}`)
      socket.onopen = () => {
        retry = 0
        setConnected(true)
        ping = setInterval(() => socket?.readyState === WebSocket.OPEN && socket.send('ping'), 25_000)
      }
      socket.onmessage = (msg) => {
        if (msg.data === 'pong') return
        try {
          const event = JSON.parse(msg.data) as ClaimEvent
          queryClient.invalidateQueries({ queryKey: ['claims'] })
          queryClient.invalidateQueries({ queryKey: ['claim', event.claimId] })
          queryClient.invalidateQueries({ queryKey: ['notifications'] })
          queryClient.invalidateQueries({ queryKey: ['analytics'] })
          handler.current?.(event)
        } catch {
          /* ignore malformed */
        }
      }
      socket.onclose = () => {
        setConnected(false)
        clearInterval(ping)
        if (stopped) return
        retry = Math.min(retry + 1, 6)
        timer = setTimeout(connect, 500 * 2 ** retry)
      }
    }
    connect()
    return () => {
      stopped = true
      clearTimeout(timer)
      clearInterval(ping)
      socket?.close()
    }
  }, [enabled, queryClient])

  return connected
}
