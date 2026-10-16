import { Outlet } from 'react-router'
import { DashboardShell } from '../components/navigation'
import { creatorNavItems } from '../constants/navigation'

export default function CreatorLayout() {
  return (
    <DashboardShell brandLabel="Creator Studio" navItems={creatorNavItems}>
      <Outlet />
    </DashboardShell>
  )
}