import type { Category, Channel, ChatMessage, Stream } from '../types/domain'
import type { IconName } from '../components/ui/icons'

/** Chaîne suivie affichée dans la sidebar « Pour vous ». */
export interface FollowedChannel {
  id: string
  slug: string
  name: string
  category: string
  viewers: number
  isLive: boolean
  followers: number
}

/** Chaînes suivies (données fictives de démonstration). */
export const FOLLOWED_CHANNELS: FollowedChannel[] = [
  { id: 'c-zeka', slug: 'zekatv', name: 'ZekaTV', category: 'Fortnite', viewers: 12400, isLive: true, followers: 214000 },
  { id: 'c-moussa', slug: 'moussagaming', name: 'MoussaGaming', category: 'Call of Duty', viewers: 8700, isLive: true, followers: 156000 },
  { id: 'c-aya', slug: 'ayamusic', name: 'AyaMusic', category: 'Musique', viewers: 5200, isLive: true, followers: 98000 },
  { id: 'c-sport', slug: 'levraisport', name: 'LeVraiSport', category: 'Football', viewers: 3900, isLive: true, followers: 87000 },
  { id: 'c-koffi', slug: 'koffilive', name: 'KoffiLive', category: 'IRL', viewers: 2100, isLive: true, followers: 43000 },
]

/** Catégories de la sidebar (icônes = clés du set `Icon`). */
export const SIDEBAR_CATEGORIES: { name: string; icon: IconName; viewers: number }[] = [
  { name: 'Gaming', icon: 'gamepad', viewers: 48200 },
  { name: 'Musique', icon: 'music', viewers: 31500 },
  { name: 'Sport', icon: 'trending', viewers: 27800 },
  { name: 'Discussion', icon: 'chat', viewers: 19400 },
  { name: 'Podcast', icon: 'mic', viewers: 8600 },
  { name: 'Éducation', icon: 'book', viewers: 7400 },
  { name: 'Technologie', icon: 'sparkles', viewers: 6900 },
  { name: 'Art', icon: 'star', viewers: 4100 },
  { name: 'IRL', icon: 'video', viewers: 12200 },
  { name: 'Cuisine', icon: 'heart', viewers: 5300 },
  { name: 'Business', icon: 'film', viewers: 3200 },
]

/** Slide du carousel hero de la homepage. */
export interface HeroSlide {
  id: string
  channelId: string
  channelSlug: string
  channelFollowers: number
  title: string
  description: string
  category: string
  viewersCount: number
  tags: string[]
  verified: boolean
}

/** Lives mis en avant (démonstration, remplacés par l'API). */
export const HERO_SLIDES: HeroSlide[] = [
  {
    id: 's1',
    channelId: 'c-zeka',
    channelSlug: 'zekatv',
    channelFollowers: 214000,
    title: 'RANKED + TRYHARD | ROAD TO TOP 1 AFRICA',
    description: 'On est là pour la gagne ! 🔥',
    category: 'Call of Duty: Warzone',
    viewersCount: 12400,
    tags: ['Français', 'FPS', 'Call of Duty', 'Ranked'],
    verified: true,
  },
  {
    id: 's2',
    channelId: 'c-moussa',
    channelSlug: 'moussagaming',
    channelFollowers: 156000,
    title: 'TOURNOI DU WEEK-END — ON VA TOUT CASSER',
    description: 'Demi-finales en direct, pronos ouverts dans le chat.',
    category: 'Fortnite',
    viewersCount: 8700,
    tags: ['Français', 'Esport', 'Battle Royale'],
    verified: true,
  },
  {
    id: 's3',
    channelId: 'c-aya',
    channelSlug: 'ayamusic',
    channelFollowers: 98000,
    title: 'SESSION STUDIO + BLIND TEST AVEC LE CHAT',
    description: 'Vous choisissez les sons, je les rejoue en direct.',
    category: 'Musique',
    viewersCount: 5200,
    tags: ['Live Music', 'Interactif', 'Français'],
    verified: true,
  },
]

/** Lives en direct (démonstration, remplacés par l'API). */
export const LIVE_STREAMS: (Stream & { tags: string[] })[] = [
  { id: 's1', channelId: 'c-zeka', title: 'RANKED + TRYHARD | ROAD TO TOP 1 AFRICA', category: 'Call of Duty: Warzone', viewersCount: 12400, status: 'LIVE', language: 'fr', tags: ['Français', 'Ranked'] },
  { id: 's2', channelId: 'c-moussa', title: 'Tournoi Fortnite — demi-finales en direct', category: 'Fortnite', viewersCount: 8700, status: 'LIVE', language: 'fr', tags: ['Tournoi', 'Esport'] },
  { id: 's3', channelId: 'c-aya', title: 'Session studio + blind test avec le chat', category: 'Musique', viewersCount: 5200, status: 'LIVE', language: 'fr', tags: ['Live Music', 'Interactif'] },
  { id: 's4', channelId: 'c-sport', title: 'On refait le match — débats foot africain', category: 'Football', viewersCount: 3900, status: 'LIVE', language: 'fr', tags: ['Débat', 'Foot'] },
  { id: 's5', channelId: 'c-koffi', title: 'IRL au marché — dégustation street-food', category: 'IRL', viewersCount: 2100, status: 'LIVE', language: 'fr', tags: ['IRL', 'Cuisine'] },
]

/** Nom de chaîne par id (résolution mock → API plus tard). */
const CHANNEL_NAMES: Record<string, string> = {
  'c-zeka': 'ZekaTV',
  'c-moussa': 'MoussaGaming',
  'c-aya': 'AyaMusic',
  'c-sport': 'LeVraiSport',
  'c-koffi': 'KoffiLive',
}

export function channelNameFor(channelId: string): string {
  return CHANNEL_NAMES[channelId] ?? channelId
}

/** Slug de chaîne par id mock (résolution mock → API plus tard). */
export function channelSlugFor(channelId: string): string | null {
  return FOLLOWED_CHANNELS.find((channel) => channel.id === channelId)?.slug ?? null
}

/** Catégories populaires (démonstration). */
export const POPULAR_CATEGORIES: Category[] = [
  { id: 'cat-gaming', name: 'Gaming', slug: 'gaming', viewersCount: 48200, streamsCount: 412 },
  { id: 'cat-musique', name: 'Musique', slug: 'musique', viewersCount: 31500, streamsCount: 238 },
  { id: 'cat-sport', name: 'Sport', slug: 'sport', viewersCount: 27800, streamsCount: 186 },
  { id: 'cat-discussion', name: 'Discussion', slug: 'discussion', viewersCount: 19400, streamsCount: 204 },
  { id: 'cat-just-chatting', name: 'Just Chatting', slug: 'just-chatting', viewersCount: 15800, streamsCount: 176 },
  { id: 'cat-irl', name: 'IRL', slug: 'irl', viewersCount: 12200, streamsCount: 97 },
  { id: 'cat-tech', name: 'Technologie', slug: 'technologie', viewersCount: 6900, streamsCount: 64 },
  { id: 'cat-education', name: 'Éducation', slug: 'education', viewersCount: 7400, streamsCount: 71 },
  { id: 'cat-cuisine', name: 'Cuisine', slug: 'cuisine', viewersCount: 5300, streamsCount: 58 },
  { id: 'cat-art', name: 'Art', slug: 'art', viewersCount: 4100, streamsCount: 41 },
]

/** Créateurs émergents (démonstration). */
export const EMERGING_CREATORS: Channel[] = [
  { id: 'c-aya', userId: 'u-aya', name: 'AyaMusic', slug: 'ayamusic', followersCount: 98000, isLive: true },
  { id: 'c-moussa', userId: 'u-moussa', name: 'MoussaGaming', slug: 'moussagaming', followersCount: 156000, isLive: true },
  { id: 'c-koffi', userId: 'u-koffi', name: 'KoffiLive', slug: 'koffilive', followersCount: 43000, isLive: true },
  { id: 'c-sport', userId: 'u-sport', name: 'LeVraiSport', slug: 'levraisport', followersCount: 87000, isLive: true },
]

/** Top cheers du jour (démonstration). */
export const TOP_CHEERS: { rank: number; name: string; points: number }[] = [
  { rank: 1, name: 'Kylian_237', points: 10000 },
  { rank: 2, name: 'MomoLive', points: 5000 },
  { rank: 3, name: 'Djo_Boss', points: 2500 },
]

/** Messages du chat de démonstration (remplacés par le WebSocket). */
export const MOCK_CHAT_MESSAGES: ChatMessage[] = [
  { id: 'm1', streamId: 's1', userId: 'u21', username: 'Tchop_237', content: 'Wish la team ! 🔥', createdAt: '' },
  { id: 'm2', streamId: 's1', userId: 'u22', username: 'Mouhamed', content: "C'est propre ce gameplay !", createdAt: '' },
  { id: 'm3', streamId: 's1', userId: 'u23', username: 'Aya_queen', content: 'Le goat 🔥', createdAt: '' },
  { id: 'm4', streamId: 's1', userId: 'u24', username: 'Koffi99', content: "T'es trop fort frère !", createdAt: '' },
  { id: 'm5', streamId: 's1', userId: 'u25', username: 'FanDeGaming', content: 'Quel clutch !', createdAt: '' },
  { id: 'm6', streamId: 's1', userId: 'u26', username: 'Bastos_26', content: 'On veut du duo avec Moussa !', createdAt: '' },
  { id: 'm7', streamId: 's1', userId: 'u27', username: 'Linaaa', content: "La prochaine game c'est quoi ?", createdAt: '' },
  { id: 'm8', streamId: 's1', userId: 'u28', username: 'RayanC', content: 'Présent 🔥', createdAt: '' },
  { id: 'm9', streamId: 's1', userId: 'u29', username: 'Cedric_225', content: 'GG GG GG', createdAt: '' },
  { id: 'm10', streamId: 's1', userId: 'u30', username: 'Tata_Bellie', content: 'Incroyable !', createdAt: '' },
  { id: 'm11', streamId: 's1', userId: 'u31', username: 'Nico_237', content: "T'as un setup de malade !", createdAt: '' },
  { id: 'm12', streamId: 's1', userId: 'u32', username: 'LeVraiTchad', content: "FLICK c'est la vie !", createdAt: '' },
]
