import { Outlet } from 'react-router'
import { Navbar, Sidebar, MobileBottomNav, MobileBottomNavSpacer, MobileHeader } from '../components/navigation'
import { userNavItems } from '../constants/navigation'
import { useFollowedChannels } from '../features/follow/useFollowedChannels'

export default function UserLayout() {
  const followed = useFollowedChannels()
  return (
    <div className="flex min-h-screen flex-col bg-bg-primary">
      <MobileHeader />
      <Navbar />
      <div className="flex flex-1">
        <Sidebar
          items={userNavItems}
          followed={followed}
          showCategories
          showBrandHeader={false}
          hideOnMobile
          className="sticky top-16 h-[calc(100vh-4rem)]"
        />
        <main className="min-w-0 flex-1 p-3 lg:p-4">
          <Outlet />
        </main>
      </div>
      <MobileBottomNavSpacer />
      <MobileBottomNav />
    </div>
  )
}