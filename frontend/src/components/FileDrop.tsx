import { useRef, useState } from 'react'
import clsx from 'clsx'
import { FileText, UploadCloud, X } from 'lucide-react'
import { fileSize } from '../lib/format'

export const ACCEPTED = ['application/pdf', 'image/png', 'image/jpeg', 'text/plain']
const MAX = 10 * 1024 * 1024

export default function FileDrop({ files, onChange, max = 5 }: { files: File[]; onChange: (f: File[]) => void; max?: number }) {
  const input = useRef<HTMLInputElement>(null)
  const [over, setOver] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const add = (list: FileList | null) => {
    if (!list) return
    setError(null)
    const next = [...files]
    for (const f of Array.from(list)) {
      if (!ACCEPTED.includes(f.type)) {
        setError(`${f.name}: only PDF, PNG, JPG or TXT`)
        continue
      }
      if (f.size > MAX) {
        setError(`${f.name} is over 10 MB`)
        continue
      }
      if (next.length >= max) {
        setError(`Up to ${max} files`)
        break
      }
      next.push(f)
    }
    onChange(next)
  }

  return (
    <div>
      <div
        onDragOver={(e) => {
          e.preventDefault()
          setOver(true)
        }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => {
          e.preventDefault()
          setOver(false)
          add(e.dataTransfer.files)
        }}
        onClick={() => input.current?.click()}
        className={clsx(
          'flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed px-4 py-6 text-center transition',
          over ? 'border-brand-500 bg-brand-50' : 'border-slate-300 hover:border-slate-400',
        )}
      >
        <UploadCloud className="size-6 text-slate-400" />
        <p className="mt-2 text-sm text-slate-700">
          <span className="font-medium text-brand-600">Choose files</span> or drag them here
        </p>
        <p className="text-xs text-slate-500">Bills, invoices, estimates, photos. PDF, PNG, JPG, TXT up to 10 MB.</p>
        <input
          ref={input}
          type="file"
          multiple
          accept={ACCEPTED.join(',')}
          className="hidden"
          onChange={(e) => {
            add(e.target.files)
            e.target.value = ''
          }}
        />
      </div>
      {error && <p className="mt-1 text-xs text-rose-600">{error}</p>}
      {files.length > 0 && (
        <ul className="mt-3 space-y-2">
          {files.map((f, i) => (
            <li key={f.name + i} className="flex items-center gap-3 rounded-lg border border-slate-200 px-3 py-2 text-sm">
              <FileText className="size-4 text-slate-400" />
              <span className="flex-1 truncate">{f.name}</span>
              <span className="text-xs text-slate-500">{fileSize(f.size)}</span>
              <button type="button" onClick={() => onChange(files.filter((_, j) => j !== i))} className="text-slate-400 hover:text-rose-600" aria-label={`Remove ${f.name}`}>
                <X className="size-4" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
