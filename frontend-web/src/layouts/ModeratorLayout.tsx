import { Outlet } from 'react-router'
import { DashboardShell } from '../components/navigation'
import { moderatorNavItems } from '../constants/navigation'

export default function ModeratorLayout() {
  return (
    <DashboardShell brandLabel="Modération" navItems={moderatorNavItems}>
      <Outlet />
    </DashboardShell>
  )
}