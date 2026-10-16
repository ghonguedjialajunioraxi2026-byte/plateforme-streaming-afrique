import type {
  FlickCategory,
  FlickChatMessage,
  FlickCountryTrend,
  FlickCreator,
  FlickFeatured,
  FlickLive,
} from '../types/flick'

/** Formatte 12400 → "12.4K". */
export function formatViewers(count: number): string {
  if (count >= 1_000_000) return `${(count / 1_000_000).toFixed(1).replace('.', ',')}M`
  if (count >= 1_000) return `${(count / 1_000).toFixed(1).replace('.', ',')}K`
  return String(count)
}

export const FLICK_FEATURED: FlickFeatured[] = [
  {
    id: 's1',
    title: 'RANKED + TRYHARD — ROAD TO TOP 1 AFRICA',
    description:
      'Zeka défend son rang de n°1 camerounais sur Warzone. Drops activés, prédictions ouvertes dans le chat.',
    category: 'Call of Duty: Warzone',
    language: 'Français',
    viewers: 12400,
    startedAt: 'Il y a 2 h 14',
    tags: ['Gaming', 'Afrique', 'FPS', 'Ranked'],
    artwork: '🎮',
    streamer: {
      id: 'c-zeka',
      name: 'ZekaTV',
      slug: 'zekatv',
      avatarHue: 187,
      verified: true,
      country: 'Cameroun',
      countryCode: 'CM',
      followers: 214000,
    },
  },
  {
    id: 's2',
    title: 'TOURNOI DU WEEK-END — DEMI-FINALES EN DIRECT',
    description:
      'Tournoi Fortnite inter-pays : Cameroun vs Côte d’Ivoire. 2 000 € de cashprize, cast en français.',
    category: 'Fortnite',
    language: 'Français',
    viewers: 8700,
    startedAt: 'Il y a 1 h 05',
    tags: ['Esport', 'Afrique', 'Battle Royale'],
    artwork: '🏆',
    streamer: {
      id: 'c-moussa',
      name: 'MoussaGaming',
      slug: 'moussagaming',
      avatarHue: 210,
      verified: true,
      country: 'Sénégal',
      countryCode: 'SN',
      followers: 156000,
    },
  },
  {
    id: 's3',
    title: 'SESSION STUDIO + BLIND TEST AVEC LE CHAT',
    description:
      'Aya rejoue en direct les sons choisis par le chat : afrobeats, amapiano, coupé-décalé.',
    category: 'Musique',
    language: 'Français',
    viewers: 5200,
    startedAt: 'Il y a 48 min',
    tags: ['Live Music', 'Afrobeats', 'Interactif'],
    artwork: '🎶',
    streamer: {
      id: 'c-aya',
      name: 'AyaMusic',
      slug: 'ayamusic',
      avatarHue: 320,
      verified: true,
      country: "Côte d'Ivoire",
      countryCode: 'CI',
      followers: 98000,
    },
  },
]

export const FLICK_LIVES: FlickLive[] = [
  ...FLICK_FEATURED,
  {
    id: 's4',
    title: 'ON REFAIT LE MATCH — DÉBATS FOOT AFRICAIN',
    category: 'Football',
    language: 'Français',
    viewers: 3900,
    startedAt: 'Il y a 1 h 32',
    tags: ['Débat', 'Foot', 'Afrique'],
    artwork: '⚽',
    streamer: {
      id: 'c-sport',
      name: 'LeVraiSport',
      slug: 'levraisport',
      avatarHue: 140,
      verified: true,
      country: 'Nigeria',
      countryCode: 'NG',
      followers: 87000,
    },
  },
  {
    id: 's5',
    title: 'IRL AU MARCHÉ — STREET-FOOD DE DOUALA',
    category: 'IRL',
    language: 'Français',
    viewers: 2100,
    startedAt: 'Il y a 35 min',
    tags: ['IRL', 'Cuisine'],
    artwork: '🍲',
    streamer: {
      id: 'c-koffi',
      name: 'KoffiLive',
      slug: 'koffilive',
      avatarHue: 35,
      verified: true,
      country: 'Cameroun',
      countryCode: 'CM',
      followers: 43000,
    },
  },
  {
    id: 's6',
    title: 'CODE EN DIRECT — ON BUILD UNE APP AFROTECH',
    category: 'Technologie',
    language: 'Français',
    viewers: 1800,
    startedAt: 'Il y a 58 min',
    tags: ['Code', 'Afrotech'],
    artwork: '💻',
    streamer: {
      id: 'c-tech',
      name: 'DevDakar',
      slug: 'devdakar',
      avatarHue: 260,
      verified: true,
      country: 'Sénégal',
      countryCode: 'SN',
      followers: 31000,
    },
  },
  {
    id: 's7',
    title: 'PODCAST — ENTREPRENDRE À ABIDJAN',
    category: 'Podcast',
    language: 'Français',
    viewers: 1400,
    startedAt: 'Il y a 22 min',
    tags: ['Business', 'Discussion'],
    artwork: '🎙️',
    streamer: {
      id: 'c-pod',
      name: 'AbidjanTalk',
      slug: 'abidjantalk',
      avatarHue: 45,
      verified: false,
      country: "Côte d'Ivoire",
      countryCode: 'CI',
      followers: 18000,
    },
  },
  {
    id: 's8',
    title: 'AMAPIANO NIGHT — SET DJ EN DIRECT DE LAGOS',
    category: 'Musique',
    language: 'English',
    viewers: 9600,
    startedAt: 'Il y a 1 h 47',
    tags: ['Amapiano', 'DJ Set'],
    artwork: '🪩',
    streamer: {
      id: 'c-lagos',
      name: 'LagosNights',
      slug: 'lagosnights',
      avatarHue: 290,
      verified: true,
      country: 'Nigeria',
      countryCode: 'NG',
      followers: 122000,
    },
  },
]

export const FLICK_CATEGORIES: FlickCategory[] = [
  { id: 'gaming', slug: 'gaming', name: 'Gaming', viewers: 48200, lives: 412, artwork: '🎮', gradient: 'from-cyan-500/40 via-blue-600/20 to-transparent' },
  { id: 'musique', slug: 'musique', name: 'Musique', viewers: 31500, lives: 238, artwork: '🎵', gradient: 'from-fuchsia-500/40 via-purple-600/20 to-transparent' },
  { id: 'sport', slug: 'sport', name: 'Sport', viewers: 27800, lives: 186, artwork: '⚽', gradient: 'from-emerald-500/40 via-green-600/20 to-transparent' },
  { id: 'discussion', slug: 'discussion', name: 'Discussion', viewers: 19400, lives: 204, artwork: '💬', gradient: 'from-amber-500/40 via-orange-600/20 to-transparent' },
  { id: 'podcast', slug: 'podcast', name: 'Podcast', viewers: 8600, lives: 92, artwork: '🎙️', gradient: 'from-rose-500/40 via-red-600/20 to-transparent' },
  { id: 'education', slug: 'education', name: 'Éducation', viewers: 7400, lives: 71, artwork: '📚', gradient: 'from-sky-500/40 via-cyan-600/20 to-transparent' },
  { id: 'technologie', slug: 'technologie', name: 'Technologie', viewers: 6900, lives: 64, artwork: '💻', gradient: 'from-indigo-500/40 via-blue-600/20 to-transparent' },
  { id: 'irl', slug: 'irl', name: 'IRL', viewers: 12200, lives: 97, artwork: '🌍', gradient: 'from-lime-500/40 via-emerald-600/20 to-transparent' },
]

export const FLICK_CREATORS: FlickCreator[] = [
  {
    id: 'c-aya', name: 'AyaMusic', slug: 'ayamusic', avatarHue: 320, verified: true,
    country: "Côte d'Ivoire", countryCode: 'CI', followers: 98000,
    headline: 'Sessions studio live & blind tests afrobeats', liveNow: true, viewers: 5200,
  },
  {
    id: 'c-koffi', name: 'KoffiLive', slug: 'koffilive', avatarHue: 35, verified: true,
    country: 'Cameroun', countryCode: 'CM', followers: 43000,
    headline: 'IRL, street-food et découvertes de Douala', liveNow: true, viewers: 2100,
  },
  {
    id: 'c-tech', name: 'DevDakar', slug: 'devdakar', avatarHue: 260, verified: true,
    country: 'Sénégal', countryCode: 'SN', followers: 31000,
    headline: 'Code, afrotech et build in public', liveNow: true, viewers: 1800,
  },
  {
    id: 'c-lagos', name: 'LagosNights', slug: 'lagosnights', avatarHue: 290, verified: true,
    country: 'Nigeria', countryCode: 'NG', followers: 122000,
    headline: 'DJ sets amapiano & afrohouse', liveNow: true, viewers: 9600,
  },
]

export const FLICK_TRENDS: FlickCountryTrend[] = [
  { country: 'Cameroun', code: 'CM', viewers: 28400, lives: 132, topCategory: 'Gaming' },
  { country: "Côte d'Ivoire", code: 'CI', viewers: 23100, lives: 118, topCategory: 'Musique' },
  { country: 'Sénégal', code: 'SN', viewers: 18700, lives: 96, topCategory: 'Esport' },
  { country: 'Nigeria', code: 'NG', viewers: 35600, lives: 214, topCategory: 'Musique' },
  { country: 'Ghana', code: 'GH', viewers: 12400, lives: 74, topCategory: 'Discussion' },
  { country: 'RDC', code: 'CD', viewers: 9800, lives: 58, topCategory: 'IRL' },
]

export const FLICK_CHAT: FlickChatMessage[] = [
  { id: 'sys-1', username: 'FLICK', color: 'var(--color-primary-500)', message: 'Bienvenue dans le chat ! Respecte la communauté : pas de spam, pas de haine. Amusez-vous bien 🙌', isSystem: true },
  { id: 'm1', username: 'Tchop_237', color: '#EF4444', message: 'Wish la team ! 🔥', badge: 'Abonné' },
  { id: 'm2', username: 'Mouhamed_SN', color: '#F59E0B', message: "C'est propre ce gameplay !", badge: 'VIP' },
  { id: 'm3', username: 'Aya_queen', color: '#5EEAD4', message: 'Le goat 🔥' },
  { id: 'm4', username: 'Koffi99', color: '#5AA9FF', message: "T'es trop fort frère !" },
  { id: 'm5', username: 'FanDeGaming', color: '#C07BFF', message: 'Quel clutch !', badge: 'Modo' },
  { id: 'm6', username: 'Bastos_26', color: '#FF7AD9', message: 'On veut du duo avec Moussa !' },
  { id: 'm7', username: 'Linaaa', color: '#FFB35A', message: "La prochaine game c'est quoi ?" },
  { id: 'm8', username: 'RayanC', color: '#7DE3E0', message: 'Présent depuis Abidjan 🔥' },
  { id: 'm9', username: 'Cedric_225', color: '#EF4444', message: 'GG GG GG', badge: 'Fondateur' },
  { id: 'm10', username: 'Nico_237', color: '#5AA9FF', message: "T'as un setup de malade !" },
]

export function liveById(id: string): FlickLive | undefined {
  return FLICK_LIVES.find((live) => live.id === id)
}
