import { useEffect, useState } from 'react'
import { Loader2 } from 'lucide-react'
import { onServerWaking } from '../lib/api'

/** Shown while the free-tier server is booting after being idle. */
export default function WakeBanner() {
  const [waking, setWaking] = useState(false)
  const [seconds, setSeconds] = useState(0)

  useEffect(() => onServerWaking(setWaking), [])
  useEffect(() => {
    if (!waking) {
      setSeconds(0)
      return
    }
    const t = setInterval(() => setSeconds((s) => s + 1), 1000)
    return () => clearInterval(t)
  }, [waking])

  if (!waking) return null
  return (
    <div role="status" className="fixed inset-x-0 top-0 z-50 flex justify-center p-3">
      <div className="flex max-w-lg items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 shadow-lg">
        <Loader2 className="mt-0.5 size-4 shrink-0 animate-spin" />
        <div>
          <div className="font-medium">Waking up the server… {seconds > 0 && <span className="tabular">{seconds}s</span>}</div>
          <div className="text-amber-800">
            The demo runs on free hosting that sleeps when nobody is using it. Starting takes about a minute. Your request will go through automatically.
          </div>
        </div>
      </div>
    </div>
  )
}
