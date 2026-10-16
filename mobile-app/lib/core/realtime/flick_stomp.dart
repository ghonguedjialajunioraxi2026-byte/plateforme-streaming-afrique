import 'package:stomp_dart_client/stomp_dart_client.dart';

import '../config/env.dart';

/// Temps réel FLICK — STOMP sur le `/ws` backend existant.
///
/// Réutilise l'infrastructure serveur (broker `/topic`, préfixes `/app`) :
/// aucun second système. Topics : `/topic/events/<type>`
/// (ex. chat-message), /topic/pulse (démo), présence.
/// Reconnexion avec backoff ; JAMAIS de secret dans les frames.
class FlickStomp {
  FlickStomp({this.tokenProvider});

  final String? Function()? tokenProvider;
  StompClient? _client;
  int _attempts = 0;

  bool get isConnected => _client?.connected ?? false;

  String get _url {
    final base = Env.apiBaseUrl
        .replaceFirst(RegExp(r'/api/?$'), '')
        .replaceFirst('https://', 'wss://')
        .replaceFirst('http://', 'ws://');
    return '$base/ws';
  }

  void connect({required void Function() onReady}) {
    if (isConnected) {
      onReady();
      return;
    }
    final token = tokenProvider?.call();
    _client = StompClient(
      config: StompConfig(
        url: _url,
        onConnect: (_) {
          _attempts = 0;
          onReady();
        },
        onWebSocketError: (_) => _scheduleReconnect(onReady),
        onStompError: (_) => _scheduleReconnect(onReady),
        onDisconnect: (_) => _scheduleReconnect(onReady),
        stompConnectHeaders: {
          if (token != null && token.isNotEmpty) 'Authorization': 'Bearer $token',
        },
        webSocketConnectHeaders: {
          if (token != null && token.isNotEmpty) 'Authorization': 'Bearer $token',
        },
      ),
    );
    _client!.activate();
  }

  void _scheduleReconnect(void Function() onReady) {
    _attempts++;
    final delay = Duration(seconds: _attempts.clamp(1, 6) * 5);
    Future.delayed(delay, () {
      if (!isConnected) connect(onReady: onReady);
    });
  }

  /// Abonnement à un topic (ex. /topic/events/chat-message).
  /// Retourne la fonction de désabonnement.
  void Function() subscribe(
      String topic, void Function(Map<String, String?> headers, String? body) onMessage) {
    final c = _client;
    if (c == null || !c.connected) return () {};
    final unsubscribe = c.subscribe(destination: topic, callback: (frame) {
      onMessage(frame.headers, frame.body);
    });
    return () => unsubscribe();
  }

  void disconnect() {
    _client?.deactivate();
    _client = null;
  }
}
