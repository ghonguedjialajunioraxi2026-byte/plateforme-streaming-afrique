import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'app_shell.dart';
import 'core/connectivity/connectivity_service.dart';
import 'core/network/api_client.dart';
import 'core/storage/app_prefs.dart';
import 'core/storage/token_store.dart';
import 'core/theme/flick_theme.dart';
import 'features/assistant/assistant_screen.dart';
import 'features/auth/auth_controller.dart';
import 'features/auth/screens/login_screen.dart';
import 'features/auth/screens/register_screen.dart';
import 'features/auth/screens/splash_screen.dart';
import 'features/categories/categories_screen.dart';
import 'features/channel/channel_screen.dart';
import 'features/clip/clip_screen.dart';
import 'features/content/content_repository.dart';
import 'features/discovery/discovery_repository.dart';
import 'features/live/live_screen.dart';
import 'features/messages/messages_screen.dart';
import 'features/search/search_screen.dart';
import 'features/settings/settings_screen.dart';
import 'features/social/social_repository.dart';
import 'features/subscriptions/subscriptions_screen.dart';
import 'features/video/video_screen.dart';
import 'features/wallet/wallet_repository.dart';
import 'features/wallet/wallet_screen.dart';

/// FLICK mobile — MaterialApp + providers + table de routes nommées.
/// State management : Provider/ChangeNotifier (choix unique, sans codegen,
/// suffisant pour ce périmètre, coût minimal sur appareils modestes).
class FlickApp extends StatelessWidget {
  const FlickApp({
    super.key,
    required this.api,
    required this.tokens,
    required this.prefs,
    required this.auth,
  });

  final ApiClient api;
  final SecureTokenStore tokens;
  final AppPrefs prefs;
  final AuthController auth;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        Provider<ApiClient>.value(value: api),
        Provider<SecureTokenStore>.value(value: tokens),
        Provider<AppPrefs>.value(value: prefs),
        ChangeNotifierProvider<AuthController>.value(value: auth),
        ChangeNotifierProvider(
            create: (_) => ConnectivityService()),
        Provider<DiscoveryRepository>(
            create: (c) => DiscoveryRepository(c.read<ApiClient>())),
        Provider<CategoriesRepository>(
            create: (c) => CategoriesRepository(
                c.read<ApiClient>(), c.read<AppPrefs>())),
        Provider<ContentRepository>(
            create: (c) => ContentRepository(c.read<ApiClient>())),
        Provider<ChannelRepository>(
            create: (c) => ChannelRepository(c.read<ApiClient>())),
        Provider<ProfileRepository>(
            create: (c) => ProfileRepository(c.read<ApiClient>())),
        Provider<NotificationsRepository>(
            create: (c) =>
                NotificationsRepository(c.read<ApiClient>())),
        Provider<MessagesRepository>(
            create: (_) => MessagesRepository()),
        Provider<WalletRepository>(
            create: (c) => WalletRepository(c.read<ApiClient>())),
      ],
      child: MaterialApp(
        title: 'FLICK — Streaming en direct',
        debugShowCheckedModeBanner: false,
        theme: FlickTheme.dark,
        initialRoute: '/',
        onGenerateRoute: _routes,
      ),
    );
  }

  static Route<dynamic> _routes(RouteSettings settings) {
    Widget page;
    switch (settings.name) {
      case '/':
        page = const SplashScreen();
      case '/login':
        page = const LoginScreen();
      case '/register':
        page = const RegisterScreen();
      case '/home':
        page = const MainShell();
      case '/search':
        page = SearchScreen(
            initialQuery: (settings.arguments as String?) ?? '');
      case '/categories':
        page = const CategoriesScreen();
      case '/live':
        page = LiveScreen(streamId: _asInt(settings.arguments));
      case '/channel':
        page = ChannelScreen(slug: '${settings.arguments ?? ''}');
      case '/video':
        page = VideoScreen(videoId: _asInt(settings.arguments));
      case '/clip':
        page = ClipScreen(clipId: _asInt(settings.arguments));
      case '/settings':
        page = const SettingsScreen();
      case '/wallet':
        page = const WalletScreen();
      case '/subscriptions':
        page = const SubscriptionsScreen();
      case '/messages':
        page = const MessagesScreen();
      case '/conversation':
        page = ConversationScreen(conversationId: settings.arguments);
      case '/assistant':
        page = const AssistantScreen();
      default:
        page = Scaffold(
          appBar: AppBar(title: const Text('FLICK')),
          body: Center(
              child: Text('Route inconnue : ${settings.name}')),
        );
    }
    return MaterialPageRoute(
        builder: (_) => page, settings: settings);
  }

  static int _asInt(Object? v) =>
      v is int ? v : int.tryParse('$v') ?? 0;
}
