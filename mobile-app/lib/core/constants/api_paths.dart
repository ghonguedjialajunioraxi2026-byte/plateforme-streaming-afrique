/// Chemins REST — miroir EXACT des @RequestMapping backend.
/// Aucune URL backend en dur ailleurs : tout passe par ApiClient.
class ApiPaths {
  const ApiPaths._();

  // --- Système ---
  static const String health = '/health';
  static const String info = '/info';

  // --- Auth ---
  static const String authRegister = '/auth/register';
  static const String authLogin = '/auth/login';
  static const String authGoogle = '/auth/google';
  static const String authRefresh = '/auth/refresh';
  static const String authLogout = '/auth/logout';
  static const String authMe = '/auth/me';

  // --- Découverte / contenus ---
  static const String trending = '/discovery/trending';
  static String trendingByCountry(String country) => '/discovery/trending/$country';
  static const String emergingCreators = '/discovery/creators/emerging';
  static const String categories = '/categories';
  static const String statsLiveCount = '/stats/live-streams-count';

  // --- Lives ---
  static const String streams = '/streams';
  static String stream(int id) => '/streams/$id';
  static String streamStart(int id) => '/streams/$id/start';
  static String streamEnd(int id) => '/streams/$id/end';
  static String streamClips(int streamId) => '/streams/$streamId/clips';
  static String streamChat(int streamId) => '/streams/$streamId/chat';

  // --- VOD / clips ---
  static const String videosFeed = '/videos/feed';
  static String video(int id) => '/videos/$id';
  static String videoPlayback(int id) => '/videos/$id/playback';
  static String videoView(int id) => '/videos/$id/view';
  static const String clipsFeed = '/clips/feed';
  static String clip(int id) => '/clips/$id';
  static String clipPlayback(int id) => '/clips/$id/playback';
  static String clipView(int id) => '/clips/$id/view';

  // --- Créateurs / chaînes ---
  static const String creators = '/creators';
  static String creatorBySlug(String slug) => '/creators/slug/$slug';
  static String creatorVideos(int id) => '/creators/$id/videos';
  static String creatorClips(int id) => '/creators/$id/clips';

  // --- Follows ---
  static const String follows = '/follows';
  static const String isFollowing = '/follows/is-following';
  static String followingOf(int followerId) => '/follows/$followerId/following';
  static const String followersCount = '/follows/followers-count';
  static const String followingCount = '/follows/following-count';

  // --- Profil ---
  static const String profileMe = '/profiles/me';
  static String profileByUsername(String username) => '/profiles/username/$username';
  static const String profileAvatar = '/profiles/me/avatar';

  // --- Wallet / paiements ---
  static const String walletMe = '/wallet/me';
  static const String walletTransactions = '/wallet/transactions';
  static const String coinPackages = '/coin-packages';
  static const String myPayments = '/payments/mine';
  static String payment(int id) => '/payments/$id';
  static const String paymentProviders = '/payments/providers';

  // --- Notifications / présence ---
  static const String notifications = '/notifications';
  static const String notificationsUnread = '/notifications/unread-count';
  static String notificationRead(int id) => '/notifications/$id/read';
  static String presenceUser(int id) => '/presence/users/$id';

  // --- Région (Africa+) ---
  static const String countries = '/countries';
  static const String locales = '/locales';
  static const String currencies = '/currencies';
  static const String regionConfig = '/config/region';

  // --- IA (backend uniquement, jamais de clé côté mobile) ---
  static const String aiAssistant = '/ai/assistant';

  // --- Événements temps réel (transport chat démo, cf. ChatSheet) ---
  static const String eventsPublish = '/events/publish';

  // --- Dashboard créateur (lecture) ---
  static const String creatorDashboard = '/creator/dashboard';
  static const String creatorAnalytics = '/creator/analytics';
  static const String creatorRevenue = '/creator/revenue';
}
