import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Bell, FileStack, LayoutDashboard, LogOut, Menu, PlusCircle, ShieldCheck, Users, X } from 'lucide-react'
import { api } from '../lib/api'
import { isStaff, useAuth } from '../lib/auth'
import { relative, titleCase } from '../lib/format'
import { useLiveEvents } from '../lib/useLiveEvents'
import { useToast } from '../lib/toast'
import type { NotificationFeed } from '../lib/types'

export function Logo({ dark }: { dark?: boolean }) {
  return (
    <span className="flex items-center gap-2">
      <span className="flex size-8 items-center justify-center rounded-lg bg-brand-600 shadow-sm">
        <svg viewBox="0 0 32 32" className="size-5" aria-hidden>
          <path d="M9 17l4.5 4.5L23 11" stroke="#fff" strokeWidth="3.2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
      <span className={clsx('text-lg font-semibold tracking-tight', dark ? 'text-white' : 'text-slate-900')}>ClaimFlow</span>
    </span>
  )
}

export default function Layout() {
  const { user, logout, hasRole } = useAuth()
  const staff = isStaff(user)
  const toast = useToast()
  const [menuOpen, setMenuOpen] = useState(false)
  const live = useLiveEvents(!!user, (e) => {
    if (e.message && staff && e.type === 'CLAIM_SUBMITTED') toast(e.message, 'info')
  })

  const nav = [
    staff && { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/claims', label: staff ? 'Work queue' : 'My claims', icon: FileStack },
    !staff && { to: '/claims/new', label: 'File a claim', icon: PlusCircle },
    { to: '/policies', label: 'Policies', icon: ShieldCheck },
    hasRole('ADMIN') && { to: '/users', label: 'Users', icon: Users },
  ].filter(Boolean) as { to: string; label: string; icon: typeof FileStack }[]

  const sidebar = (
    <div className="flex h-full flex-col bg-ink px-3 py-4">
      <Link to="/" className="px-2 pb-6" onClick={() => setMenuOpen(false)}>
        <Logo dark />
      </Link>
      <nav className="flex-1 space-y-1">
        {nav.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/claims'}
            onClick={() => setMenuOpen(false)}
            className={({ isActive }) =>
              clsx(
                'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition',
                isActive ? 'bg-white/10 text-white' : 'text-slate-400 hover:bg-white/5 hover:text-white',
              )
            }
          >
            <Icon className="size-4" />
            {label}
          </NavLink>
        ))}
      </nav>
      <div className="mt-4 border-t border-white/10 px-2 pt-4">
        <div className="truncate text-sm font-medium text-white">{user?.fullName}</div>
        <div className="flex items-center justify-between">
          <span className="text-xs text-slate-400">{user && titleCase(user.role)}</span>
          <span className="flex items-center gap-1.5 text-xs text-slate-400" title={live ? 'Live updates on' : 'Reconnecting...'}>
            <span className={clsx('size-1.5 rounded-full', live ? 'bg-emerald-400' : 'bg-amber-400')} />
            {live ? 'Live' : 'Offline'}
          </span>
        </div>
        <button
          onClick={logout}
          className="mt-3 flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-slate-400 hover:bg-white/5 hover:text-white"
        >
          <LogOut className="size-4" /> Sign out
        </button>
      </div>
    </div>
  )

  return (
    <div className="flex h-full">
      <aside className="hidden w-60 shrink-0 lg:block">{sidebar}</aside>
      {menuOpen && (
        <div className="fixed inset-0 z-30 flex lg:hidden">
          <div className="w-64">{sidebar}</div>
          <div className="flex-1 bg-slate-900/40" onClick={() => setMenuOpen(false)} />
        </div>
      )}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-4 lg:px-8">
          <button className="rounded p-1.5 text-slate-600 hover:bg-slate-100 lg:hidden" onClick={() => setMenuOpen(true)} aria-label="Menu">
            {menuOpen ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
          <div className="lg:hidden">
            <Logo />
          </div>
          <div className="hidden text-sm text-slate-500 lg:block">
            {staff ? 'Claims operations' : 'Welcome back, ' + user?.fullName.split(' ')[0]}
          </div>
          <NotificationBell />
        </header>
        <main className="flex-1 overflow-y-auto">
          <div className="mx-auto max-w-7xl px-4 py-6 lg:px-8 lg:py-8">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}

function NotificationBell() {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.get<NotificationFeed>('/notifications'),
    refetchInterval: 60_000,
  })
  const readAll = useMutation({
    mutationFn: () => api.post('/notifications/read-all'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })
  const readOne = useMutation({
    mutationFn: (id: number) => api.post(`/notifications/${id}/read`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="relative rounded-lg p-2 text-slate-600 hover:bg-slate-100"
        aria-label={`Notifications (${data?.unread ?? 0} unread)`}
      >
        <Bell className="size-5" />
        {!!data?.unread && (
          <span className="absolute right-1 top-1 flex size-4 items-center justify-center rounded-full bg-rose-500 text-[10px] font-semibold text-white">
            {data.unread > 9 ? '9+' : data.unread}
          </span>
        )}
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="card absolute right-0 z-20 mt-2 w-80 overflow-hidden">
            <div className="flex items-center justify-between border-b border-slate-200 px-4 py-2.5">
              <span className="text-sm font-semibold">Notifications</span>
              {!!data?.unread && (
                <button className="text-xs font-medium text-brand-600 hover:underline" onClick={() => readAll.mutate()}>
                  Mark all read
                </button>
              )}
            </div>
            <ul className="max-h-96 divide-y divide-slate-100 overflow-y-auto">
              {data?.items.length ? (
                data.items.map((n) => (
                  <li key={n.id}>
                    <button
                      className={clsx('block w-full px-4 py-3 text-left text-sm hover:bg-slate-50', !n.read && 'bg-brand-50/60')}
                      onClick={() => {
                        if (!n.read) readOne.mutate(n.id)
                        setOpen(false)
                        if (n.claimId) navigate(`/claims/${n.claimId}`)
                      }}
                    >
                      <div className="text-slate-700">{n.message}</div>
                      <div className="mt-0.5 text-xs text-slate-400">{relative(n.createdAt)}</div>
                    </button>
                  </li>
                ))
              ) : (
                <li className="px-4 py-8 text-center text-sm text-slate-500">You're all caught up</li>
              )}
            </ul>
          </div>
        </>
      )}
    </div>
  )
}
