import { createBrowserRouter } from 'react-router'
import PublicLayout from '../layouts/PublicLayout'
import UserLayout from '../layouts/UserLayout'
import CreatorLayout from '../layouts/CreatorLayout'
import ModeratorLayout from '../layouts/ModeratorLayout'
import AdminLayout from '../layouts/AdminLayout'
import ProtectedRoute from './ProtectedRoute'

// --- Pages publiques ---
import Home from '../pages/public/Home'
import Explorer from '../pages/public/Explorer'
import Categories from '../pages/public/Categories'
import Search from '../pages/public/Search'
import LiveView from '../pages/public/LiveView'
import LiveChat from '../pages/public/LiveChat'
import VideoView from '../pages/public/VideoView'
import ClipView from '../pages/public/ClipView'
import Channel from '../pages/public/Channel'
import Login from '../pages/public/Login'
import Register from '../pages/public/Register'
import ForgotPassword from '../pages/public/ForgotPassword'
import ResetPassword from '../pages/public/ResetPassword'
import VerifyEmail from '../pages/public/VerifyEmail'
import Beta from '../pages/public/Beta'
import Terms from '../pages/public/Terms'
import Privacy from '../pages/public/Privacy'
import CommunityRules from '../pages/public/CommunityRules'
import Support from '../pages/public/Support'
import Status from '../pages/public/Status'

// --- Pages utilisateur ---
import UserHome from '../pages/user/UserHome'
import Following from '../pages/user/Following'
import Notifications from '../pages/user/Notifications'
import Messages from '../pages/user/Messages'
import Profile from '../pages/user/Profile'
import Settings from '../pages/user/Settings'
import Wallet from '../pages/user/Wallet'
import WalletPurchase from '../pages/user/WalletPurchase'
import WalletPayment from '../pages/user/WalletPayment'
import WalletTransactions from '../pages/user/WalletTransactions'
import Subscriptions from '../pages/user/Subscriptions'
import Feedback from '../pages/user/Feedback'

// --- Pages créateur ---
import CreatorDashboard from '../pages/creator/CreatorDashboard'
import StreamManager from '../pages/creator/StreamManager'
import CreatorVideos from '../pages/creator/CreatorVideos'
import CreatorClips from '../pages/creator/CreatorClips'
import Analytics from '../pages/creator/Analytics'
import Revenue from '../pages/creator/Revenue'
import CreatorCommunity from '../pages/creator/CreatorCommunity'
import CreatorModeration from '../pages/creator/CreatorModeration'
import CreatorAI from '../pages/creator/CreatorAI'
import CreatorSettings from '../pages/creator/CreatorSettings'

// --- Pages modération ---
import ModerationDashboard from '../pages/moderator/ModerationDashboard'
import ModChat from '../pages/moderator/ModChat'
import ModReports from '../pages/moderator/ModReports'
import ModUsers from '../pages/moderator/ModUsers'

// --- Pages admin ---
import AdminDashboard from '../pages/admin/AdminDashboard'
import AdminUsers from '../pages/admin/AdminUsers'
import AdminCreators from '../pages/admin/AdminCreators'
import AdminStreams from '../pages/admin/AdminStreams'
import AdminVideos from '../pages/admin/AdminVideos'
import AdminClips from '../pages/admin/AdminClips'
import AdminCategories from '../pages/admin/AdminCategories'
import AdminPayments from '../pages/admin/AdminPayments'
import AdminWithdrawals from '../pages/admin/AdminWithdrawals'
import AdminReports from '../pages/admin/AdminReports'
import AdminModeration from '../pages/admin/AdminModeration'
import AdminAdvertising from '../pages/admin/AdminAdvertising'
import AdminEvents from '../pages/admin/AdminEvents'
import AdminSettings from '../pages/admin/AdminSettings'
import AdminLogs from '../pages/admin/AdminLogs'
import AdminAi from '../pages/admin/AdminAi'
import AdminBeta from '../pages/admin/AdminBeta'
import AdminRegion from '../pages/admin/AdminRegion'
import Assistant from '../pages/user/Assistant'

// --- Autres ---
import NotFound from '../pages/NotFound'
import { MobileSplash } from '../components/mobile'

// --- Rail droit de l'accueil (chat + recommandations) ---
import { HomeRightRail } from '../components/home/HomeRightRail'

const router = createBrowserRouter([
  /* ============================================
     ACCUEIL — layout public + colonne droite
     ============================================ */
  {
    element: <PublicLayout rightRail={<HomeRightRail />} />,
    children: [{ path: '/', element: <Home /> }],
  },

  /* ============================================
     ROUTES PUBLIQUES — accessible à tous
     ============================================ */
  {
    element: <PublicLayout />,
    children: [
      { path: '/explorer', element: <Explorer /> },
      { path: '/categories', element: <Categories /> },
      { path: '/recherche', element: <Search /> },
      { path: '/live/:id', element: <LiveView /> },
      { path: '/live/:id/chat', element: <LiveChat /> },
      { path: '/video/:id', element: <VideoView /> },
      { path: '/clip/:id', element: <ClipView /> },
      { path: '/c/:slug', element: <ClipView /> },
      { path: '/channel/:slug', element: <Channel /> },
      { path: '/connexion', element: <Login /> },
      { path: '/inscription', element: <Register /> },
      { path: '/mot-de-passe-oublie', element: <ForgotPassword /> },
      { path: '/reinitialisation', element: <ResetPassword /> },
      { path: '/verification-email', element: <VerifyEmail /> },
      { path: '/beta', element: <Beta /> },
      { path: '/conditions', element: <Terms /> },
      { path: '/confidentialite', element: <Privacy /> },
      { path: '/regles', element: <CommunityRules /> },
      { path: '/support', element: <Support /> },
      { path: '/statut', element: <Status /> },
      { path: '/bienvenue', element: <MobileSplash /> },
      { path: '*', element: <NotFound /> },
    ],
  },

  /* ============================================
     ROUTES UTILISATEUR — connecté requis
     ============================================ */
  {
    element: (
      <ProtectedRoute>
        <UserLayout />
      </ProtectedRoute>
    ),
    children: [
      { path: '/home', element: <UserHome /> },
      { path: '/following', element: <Following /> },
      { path: '/notifications', element: <Notifications /> },
      { path: '/messages', element: <Messages /> },
      { path: '/profile', element: <Profile /> },
      { path: '/settings', element: <Settings /> },
      { path: '/wallet', element: <Wallet /> },
      { path: '/wallet/acheter', element: <WalletPurchase /> },
      { path: '/wallet/paiement/:id', element: <WalletPayment /> },
      { path: '/wallet/transactions', element: <WalletTransactions /> },
      { path: '/subscriptions', element: <Subscriptions /> },
      { path: '/assistant', element: <Assistant /> },
      { path: '/feedback', element: <Feedback /> },
    ],
  },

  /* ============================================
     ROUTES CRÉATEUR — rôle CREATOR requis
     ============================================ */
  {
    path: '/creator',
    element: (
      <ProtectedRoute allowedRoles={['CREATOR', 'MODERATOR', 'ADMIN', 'SUPER_ADMIN']}>
        <CreatorLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <CreatorDashboard /> },
      { path: 'stream', element: <StreamManager /> },
      { path: 'videos', element: <CreatorVideos /> },
      { path: 'clips', element: <CreatorClips /> },
      { path: 'analytics', element: <Analytics /> },
      { path: 'revenue', element: <Revenue /> },
      { path: 'community', element: <CreatorCommunity /> },
      { path: 'moderation', element: <CreatorModeration /> },
      { path: 'ai', element: <CreatorAI /> },
      { path: 'settings', element: <CreatorSettings /> },
    ],
  },

  /* ============================================
     ROUTES MODÉRATION — rôle MODERATOR+ requis
     ============================================ */
  {
    path: '/moderation',
    element: (
      <ProtectedRoute allowedRoles={['MODERATOR', 'ADMIN', 'SUPER_ADMIN']}>
        <ModeratorLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <ModerationDashboard /> },
      { path: 'chat', element: <ModChat /> },
      { path: 'reports', element: <ModReports /> },
      { path: 'users', element: <ModUsers /> },
    ],
  },

  /* ============================================
     ROUTES ADMINISTRATION — rôle ADMIN+ requis
     ============================================ */
  {
    path: '/admin',
    element: (
      <ProtectedRoute allowedRoles={['ADMIN', 'SUPER_ADMIN']}>
        <AdminLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <AdminDashboard /> },
      { path: 'users', element: <AdminUsers /> },
      { path: 'creators', element: <AdminCreators /> },
      { path: 'streams', element: <AdminStreams /> },
      { path: 'videos', element: <AdminVideos /> },
      { path: 'clips', element: <AdminClips /> },
      { path: 'categories', element: <AdminCategories /> },
      { path: 'payments', element: <AdminPayments /> },
      { path: 'withdrawals', element: <AdminWithdrawals /> },
      { path: 'reports', element: <AdminReports /> },
      { path: 'moderation', element: <AdminModeration /> },
      { path: 'advertising', element: <AdminAdvertising /> },
      { path: 'events', element: <AdminEvents /> },
      { path: 'settings', element: <AdminSettings /> },
      { path: 'logs', element: <AdminLogs /> },
      { path: 'ai', element: <AdminAi /> },
      { path: 'beta', element: <AdminBeta /> },
      { path: 'region', element: <AdminRegion /> },
    ],
  },
])

export default router