/// Environnements FLICK mobile.
///
/// URLs injectées à la compilation (jamais de secret ici) :
///   flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api
///
/// Valeurs par défaut (développement) :
/// - API Android émulateur → 10.0.2.2 (localhost de la machine hôte) ;
/// - appareil physique → IP LAN du PC (à surcharger via --dart-define).
enum FlickEnv { dev, staging, prod }

class Env {
  const Env._();

  /// Base REST SANS slash final (ex. http://10.0.2.2:8080/api).
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080/api',
  );

  /// Base HLS (lecture des lives). Le backend expose déjà playbackUrl
  /// absolues ; ce repli ne sert qu'aux URLs relatives éventuelles.
  static const String hlsBaseUrl = String.fromEnvironment(
    'HLS_BASE_URL',
    defaultValue: 'http://10.0.2.2:18080',
  );

  /// URL publique du site (partage natif des lives/vidéos/clips/chaînes).
  static const String publicSiteUrl = String.fromEnvironment(
    'PUBLIC_SITE_URL',
    defaultValue: 'https://flick.example.com',
  );

  /// Client ID OAuth Google **WEB** (serverClientId).
  ///
  /// PUBLIC par nature (visible dans le binaire et la console Google) :
  /// ce n'est PAS un secret. Il DOIT être le même que GOOGLE_CLIENT_ID
  /// côté backend : le backend n'accepte que les id_token dont l'audience
  /// (aud) est ce client. Le demander via :
  ///   flutter run --dart-define=GOOGLE_SERVER_CLIENT_ID=xxx.apps.googleusercontent.com
  /// Vide = bouton Google désactivé avec message explicite.
  /// Prérequis console Google (même projet que le backend) : client OAuth
  /// Android (SHA-1 du keystore) + client iOS + écran de consentement.
  static const String googleServerClientId = String.fromEnvironment(
    'GOOGLE_SERVER_CLIENT_ID',
    defaultValue: '',
  );

  static FlickEnv get current {
    const name = String.fromEnvironment('FLICK_ENV', defaultValue: 'dev');
    return FlickEnv.values.asNameMap()[name] ?? FlickEnv.dev;
  }

  static bool get isProd => current == FlickEnv.prod;
}
