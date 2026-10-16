import type { ReactNode } from 'react'
import { Outlet } from 'react-router'
import { MobileBottomNav, MobileBottomNavSpacer, MobileHeader } from '../navigation'
import { FlickHeader } from './FlickHeader'
import { FlickSidebar } from './FlickSidebar'

/**
 * Layout desktop FLICK : Header 64px + Sidebar 240px + contenu flexible.
 * Le panneau chat 320px est fourni par les pages qui en ont besoin.
 */
export function FlickLayout({ rightRail }: { rightRail?: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-bg-primary">
      <MobileHeader />
      <FlickHeader />
      <div className="flex flex-1 items-start">
        <FlickSidebar />
        <main className="min-w-0 flex-1 p-4 lg:p-6">
          <Outlet />
        </main>
        {rightRail}
      </div>
      <MobileBottomNavSpacer />
      <MobileBottomNav />
    </div>
  )
}
