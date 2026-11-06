import type { ReactNode } from 'react'
import { Bot, Gauge, ShieldCheck } from 'lucide-react'
import { Logo } from '../components/Layout'

export default function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-full lg:grid-cols-2">
      <div className="relative hidden overflow-hidden bg-ink p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="pointer-events-none absolute -right-24 -top-24 size-96 rounded-full bg-brand-600/30 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 -left-16 size-96 rounded-full bg-sky-500/10 blur-3xl" />
        <Logo dark />
        <div className="relative max-w-md">
          <h2 className="text-3xl font-semibold leading-tight tracking-tight">
            Insurance claims, settled in hours instead of weeks.
          </h2>
          <ul className="mt-8 space-y-5 text-sm text-slate-300">
            <li className="flex gap-3">
              <Bot className="mt-0.5 size-5 shrink-0 text-brand-500" />
              <span><b className="text-white">AI reads every bill.</b> Amounts, dates and providers are pulled out automatically.</span>
            </li>
            <li className="flex gap-3">
              <Gauge className="mt-0.5 size-5 shrink-0 text-brand-500" />
              <span><b className="text-white">Explainable fraud score.</b> Every risk point comes with a reason an adjuster can check.</span>
            </li>
            <li className="flex gap-3">
              <ShieldCheck className="mt-0.5 size-5 shrink-0 text-brand-500" />
              <span><b className="text-white">Four-eyes approvals.</b> Big or risky claims always need a manager's sign-off.</span>
            </li>
          </ul>
        </div>
        <p className="relative text-xs text-slate-500">Built with Spring Boot, React, PostgreSQL, Redis and nginx.</p>
      </div>
      <div className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <Logo />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </div>
    </div>
  )
}
