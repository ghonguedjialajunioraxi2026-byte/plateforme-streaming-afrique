import { Outlet } from 'react-router'
import { DashboardShell } from '../components/navigation'
import { adminNavItems } from '../constants/navigation'

export default function AdminLayout() {
  return (
    <DashboardShell brandLabel="Administration" navItems={adminNavItems}>
      <Outlet />
    </DashboardShell>
  )
}