import type { ReactNode } from 'react'
import { Link, Outlet } from 'react-router'
import { Navbar, Sidebar, MobileBottomNav, MobileBottomNavSpacer, MobileHeader } from '../components/navigation'
import { GuestSignupBanner } from '../components/common/GuestSignupBanner'
import { publicNavItems } from '../constants/navigation'
import { useFollowedChannels } from '../features/follow/useFollowedChannels'

interface PublicLayoutProps {
  /** Colonne droite optionnelle (ex : chat + recommandations sur l'accueil) */
  rightRail?: ReactNode
}

export default function PublicLayout({ rightRail }: PublicLayoutProps) {
  const followed = useFollowedChannels()
  return (
    <div className="flex min-h-screen flex-col bg-bg-primary">
      <MobileHeader />
      <Navbar />
      <div className="flex flex-1 items-start">
        <Sidebar
          items={publicNavItems}
          followed={followed}
          showCategories
          showBrandHeader={false}
          hideOnMobile
          className="sticky top-16 h-[calc(100vh-4rem)]"
        />
        <main className="min-w-0 flex-1 p-3 lg:p-4">
          <Outlet />
        </main>
        {rightRail}
      </div>
      <footer className="border-t border-border-default px-4 py-3 text-center text-xs text-text-muted">
        <nav aria-label="Liens légaux" className="flex flex-wrap justify-center gap-x-4 gap-y-1">
          <Link to="/conditions" className="hover:underline">Conditions</Link>
          <Link to="/confidentialite" className="hover:underline">Confidentialité</Link>
          <Link to="/regles" className="hover:underline">Règles</Link>
          <Link to="/support" className="hover:underline">Support</Link>
          <Link to="/statut" className="hover:underline">Statut</Link>
          <Link to="/beta" className="hover:underline">Bêta</Link>
        </nav>
      </footer>
      <MobileBottomNavSpacer />
      <MobileBottomNav />
      <GuestSignupBanner />
    </div>
  )
}
