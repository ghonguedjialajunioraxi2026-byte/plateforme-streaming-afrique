import type { NavItem } from '../types/navigation'

/// Tous les éléments de navigation de l'application.
/// Centralisés ici pour être réutilisés par les Sidebars et les layouts.
/// Ajouter/modifier une entrée ici met à jour toute l'app.

export const publicNavItems = [
  { to: '/', label: 'Accueil', labelKey: 'nav.home', icon: 'home' },
  { to: '/explorer', label: 'Explorer', labelKey: 'nav.explore', icon: 'compass' },
  { to: '/categories', label: 'Catégories', labelKey: 'nav.categories', icon: 'grid' },
  { to: '/following', label: 'Chaînes suivies', labelKey: 'nav.following', icon: 'heart' },
  { to: '/beta', label: 'Bêta', labelKey: 'nav.beta', icon: 'flag' },
] satisfies NavItem[]

export const userNavItems = [
  { to: '/home', label: 'Mon espace', labelKey: 'nav.myspace', icon: 'home' },
  { to: '/following', label: 'Chaînes suivies', labelKey: 'nav.following', icon: 'heart' },
  { to: '/notifications', label: 'Notifications', labelKey: 'nav.notifications', icon: 'bell' },
  { to: '/messages', label: 'Messages', labelKey: 'nav.messages', icon: 'chat' },
  { to: '/profile', label: 'Profil', labelKey: 'nav.profile', icon: 'user' },
  { to: '/wallet', label: 'Portefeuille', labelKey: 'nav.wallet', icon: 'wallet' },
  { to: '/subscriptions', label: 'Abonnements', labelKey: 'nav.subscriptions', icon: 'star' },
  { to: '/assistant', label: 'Assistant', labelKey: 'nav.assistant', icon: 'sparkles' },
  { to: '/feedback', label: 'Retour bêta', labelKey: 'nav.feedback', icon: 'chat' },
  { to: '/settings', label: 'Paramètres', labelKey: 'nav.settings', icon: 'settings' },
] satisfies NavItem[]

export const creatorNavItems = [
  { to: '/creator', label: 'Dashboard', labelKey: 'nav.dashboard', icon: 'home' },
  { to: '/creator/stream', label: 'Stream Manager', labelKey: 'nav.stream', icon: 'play' },
  { to: '/creator/videos', label: 'Vidéos', labelKey: 'nav.videos', icon: 'video' },
  { to: '/creator/clips', label: 'Clips', labelKey: 'nav.clips', icon: 'film' },
  { to: '/creator/analytics', label: 'Analytique', labelKey: 'nav.analytics', icon: 'trending' },
  { to: '/creator/revenue', label: 'Revenus', labelKey: 'nav.revenue', icon: 'wallet' },
  { to: '/creator/community', label: 'Communauté', labelKey: 'nav.community', icon: 'users' },
  { to: '/creator/moderation', label: 'Modération', labelKey: 'nav.moderation', icon: 'shield' },
  { to: '/creator/ai', label: 'IA', labelKey: 'nav.ai', icon: 'sparkles' },
  { to: '/creator/settings', label: 'Paramètres', labelKey: 'nav.settings', icon: 'settings' },
] satisfies NavItem[]

export const moderatorNavItems = [
  { to: '/moderation', label: 'Dashboard', labelKey: 'nav.dashboard', icon: 'home' },
  { to: '/moderation/chat', label: 'Chat', labelKey: 'nav.chat', icon: 'chat' },
  { to: '/moderation/reports', label: 'Signalements', labelKey: 'nav.reports', icon: 'flag' },
  { to: '/moderation/users', label: 'Utilisateurs', labelKey: 'nav.users', icon: 'users' },
] satisfies NavItem[]

export const adminNavItems = [
  { to: '/admin', label: 'Dashboard', labelKey: 'nav.dashboard', icon: 'home' },
  { to: '/admin/users', label: 'Utilisateurs', labelKey: 'nav.users', icon: 'users' },
  { to: '/admin/creators', label: 'Créateurs', labelKey: 'nav.creators', icon: 'video' },
  { to: '/admin/streams', label: 'Streams', labelKey: 'nav.streams', icon: 'play' },
  { to: '/admin/videos', label: 'Vidéos', labelKey: 'nav.videos', icon: 'film' },
  { to: '/admin/clips', label: 'Clips', labelKey: 'nav.clips', icon: 'film' },
  { to: '/admin/categories', label: 'Catégories', labelKey: 'nav.categories', icon: 'grid' },
  { to: '/admin/payments', label: 'Paiements', labelKey: 'nav.payments', icon: 'wallet' },
  { to: '/admin/withdrawals', label: 'Retraits', labelKey: 'nav.withdrawals', icon: 'coin' },
  { to: '/admin/reports', label: 'Signalements', labelKey: 'nav.reports', icon: 'flag' },
  { to: '/admin/moderation', label: 'Modération', labelKey: 'nav.moderation', icon: 'shield' },
  { to: '/admin/advertising', label: 'Publicité', labelKey: 'nav.advertising', icon: 'megaphone' },
  { to: '/admin/events', label: 'Événements', labelKey: 'nav.events', icon: 'calendar' },
  { to: '/admin/settings', label: 'Configuration', labelKey: 'nav.configuration', icon: 'settings' },
  { to: '/admin/logs', label: 'Logs', labelKey: 'nav.logs', icon: 'file-text' },
  { to: '/admin/ai', label: 'IA', labelKey: 'nav.ai', icon: 'sparkles' },
  { to: '/admin/beta', label: 'Bêta', labelKey: 'nav.beta', icon: 'flag' },
  { to: '/admin/region', label: 'Région', labelKey: 'nav.region', icon: 'compass' },
] satisfies NavItem[]