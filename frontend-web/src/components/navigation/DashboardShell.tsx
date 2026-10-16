import type { ReactNode } from 'react'
import type { NavItem } from '../../types/navigation'
import { Sidebar } from './Sidebar'
import { MobileBottomNav, MobileBottomNavSpacer } from './MobileBottomNav'
import { MobileHeader } from './MobileHeader'

interface DashboardShellProps {
  brandLabel: string
  navItems: NavItem[]
  children: ReactNode
}

/** Structure commune aux dashboards (Creator, Modération, Admin). */
export function DashboardShell({ brandLabel, navItems, children }: DashboardShellProps) {
  return (
    <div className="flex min-h-screen flex-col bg-bg-primary">
      <MobileHeader />
      <div className="flex min-h-0 flex-1">
        <Sidebar items={navItems} brandLabel={brandLabel} className="sticky top-14 h-[calc(100vh-3.5rem)] md:top-0 md:h-screen" />
        <main className="min-w-0 flex-1 p-4 lg:p-6">{children}</main>
      </div>
      <MobileBottomNavSpacer />
      <MobileBottomNav />
    </div>
  )
}