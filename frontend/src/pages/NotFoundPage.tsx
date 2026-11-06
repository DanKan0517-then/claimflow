import { Link } from 'react-router-dom'
import { FileQuestion } from 'lucide-react'
import { EmptyState } from '../components/ui'

export default function NotFoundPage() {
  return (
    <div className="card">
      <EmptyState icon={<FileQuestion className="size-6" />} title="Page not found">
        <Link to="/" className="font-medium text-brand-600 hover:underline">Go home</Link>
      </EmptyState>
    </div>
  )
}
