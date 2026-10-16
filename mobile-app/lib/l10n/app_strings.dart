/// Internationalisation minimale — FR + EN, extensible (pt, es…).
/// Aucun texte en dur dans les widgets : passer par S.
class S {
  const S(this.locale);

  final String locale;

  static const supported = ['fr', 'en'];

  static String _pick(String locale, String fr, String en) =>
      locale == 'en' ? en : fr;

  String get appTagline => _pick(locale, 'Streaming en direct', 'Live streaming');
  String get home => _pick(locale, 'Accueil', 'Home');
  String get explore => _pick(locale, 'Explorer', 'Explore');
  String get create => _pick(locale, 'Créer', 'Create');
  String get notifications => _pick(locale, 'Notifications', 'Notifications');
  String get profile => _pick(locale, 'Profil', 'Profile');
  String get search => _pick(locale, 'Rechercher', 'Search');
  String get searchHint => _pick(locale, 'Chaînes, jeux, créateurs…', 'Channels, games, creators…');
  String get categories => _pick(locale, 'Catégories', 'Categories');
  String get liveNow => _pick(locale, 'En direct', 'Live now');
  String get login => _pick(locale, 'Connexion', 'Log in');
  String get register => _pick(locale, 'Inscription', 'Sign up');
  String get continueWithGoogle =>
      _pick(locale, 'Continuer avec Google', 'Continue with Google');
  String get orDivider => _pick(locale, 'ou', 'or');
  String get googleUnavailable => _pick(
      locale,
      'Google indisponible sur cette version (contactez le support)',
      'Google unavailable in this build (contact support)');
  String get logout => _pick(locale, 'Déconnexion', 'Log out');
  String get loading => _pick(locale, 'Chargement…', 'Loading…');
  String get retry => _pick(locale, 'Réessayer', 'Retry');
  String get empty => _pick(locale, 'Rien à afficher pour le moment', 'Nothing to show yet');
  String get offline => _pick(locale, 'Hors ligne — vérifiez votre connexion', 'Offline — check your connection');
  String get unstable => _pick(locale, 'Connexion instable', 'Unstable connection');
  String get reconnecting => _pick(locale, 'Reconnexion…', 'Reconnecting…');
  String get wallet => _pick(locale, 'Portefeuille', 'Wallet');
  String get settings => _pick(locale, 'Paramètres', 'Settings');
  String get dataSaver => _pick(locale, 'Économie de données', 'Data saver');
  String get follow => _pick(locale, 'Suivre', 'Follow');
  String get following => _pick(locale, 'Suivi', 'Following');
  String get send => _pick(locale, 'Envoyer', 'Send');
  String get cancel => _pick(locale, 'Annuler', 'Cancel');
}
