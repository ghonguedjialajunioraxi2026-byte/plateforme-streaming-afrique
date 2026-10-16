import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/realtime/flick_stomp.dart';
import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../shared/models/social.dart';
import '../../../shared/models/user.dart';

/// Chat du live — branché sur l'infra temps réel EXISTANTE :
/// réception STOMP /topic/events/chat-message, envoi via
/// POST /api/events/publish (réservé aux CONNECTÉS côté backend, Bearer
/// joint par ApiClient, rate-limit 429 géré, modération/slow-mode
/// appliqués serveur ; anonyme → 401 → redirection /login).
/// Reconnexion auto, états loading/erreur/offline.
class ChatSheet extends StatefulWidget {
  const ChatSheet({super.key, required this.streamId, this.currentUser});

  final int streamId;
  final FlickUser? currentUser;

  @override
  State<ChatSheet> createState() => _ChatSheetState();
}

class _ChatSheetState extends State<ChatSheet> {
  final _messages = <ChatMessage>[];
  final _controller = TextEditingController();
  final _scroll = ScrollController();
  FlickStomp? _stomp;
  void Function()? _unsubscribe;
  bool _connected = false;
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    _messages.add(ChatMessage(
        author: 'FLICK', text: 'Bienvenue sur le chat !', system: true));
    _loadHistory();
    _connect();
  }

  /// Historique récent persisté serveur (GET /api/streams/{id}/chat, public).
  Future<void> _loadHistory() async {
    try {
      final page = await context.read<ApiClient>().get<Map<String, dynamic>>(
        ApiPaths.streamChat(widget.streamId),
        (json) => (json as Map<String, dynamic>?) ?? {},
      );
      if (!mounted) return;
      final content = (page['content'] as List?) ?? [];
      setState(() {
        for (final item in content) {
          if (item is Map<String, dynamic>) {
            _messages.add(ChatMessage.fromHistory(item));
          }
        }
      });
      _scrollToBottom();
    } catch (_) {}
  }

  void _connect() {
    final api = context.read<ApiClient>();
    final stomp = FlickStomp(tokenProvider: () => api.tokens.accessToken);
    _stomp = stomp;
    stomp.connect(onReady: () {
      if (!mounted) return;
      setState(() => _connected = true);
      _unsubscribe?.call();
      _unsubscribe = stomp.subscribe(
          '/topic/events/chat-message', (_, body) {
        if (body == null || body.isEmpty) return;
        try {
          final envelope = jsonDecode(body) as Map<String, dynamic>;
          final payload = (envelope['payload'] as Map?) ?? {};
          if ('${payload['streamId']}' != '${widget.streamId}') return;
          if (!mounted) return;
          setState(() =>
              _messages.add(ChatMessage.fromEnvelope(envelope)));
          _scrollToBottom();
        } catch (_) {}
      });
    });
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(_scroll.position.maxScrollExtent,
            duration: const Duration(milliseconds: 250),
            curve: Curves.easeOut);
      }
    });
  }

  Future<void> _send() async {
    final text = _controller.text.trim();
    if (text.isEmpty || _sending) return;
    final user = widget.currentUser;
    if (user == null) {
      Navigator.of(context).pushNamed('/login');
      return;
    }
    setState(() => _sending = true);
    try {
      await context.read<ApiClient>().post<dynamic>(
        ApiPaths.eventsPublish,
        (_) => null,
        body: {
          'type': 'CHAT_MESSAGE',
          'payload': {
            'streamId': widget.streamId,
            'authorId': user.id,
            'authorName': user.displayName,
            'message': text,
          },
        },
      );
      _controller.clear();
      setState(() => _messages.add(
          ChatMessage(author: user.displayName, text: text, mine: true)));
      _scrollToBottom();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(e.isRateLimited
                ? 'Ralentissez (mode lent actif)'
                : e.message)));
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  void dispose() {
    _unsubscribe?.call();
    _stomp?.disconnect();
    _controller.dispose();
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 320,
      decoration: BoxDecoration(
        color: FlickColors.surface,
        borderRadius: BorderRadius.circular(FlickRadius.md),
        border: Border.all(color: FlickColors.border),
      ),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(
                horizontal: FlickSpacing.md, vertical: FlickSpacing.sm),
            child: Row(
              children: [
                Icon(
                    _connected ? Icons.bolt : Icons.bolt_outlined,
                    size: 14,
                    color: _connected
                        ? FlickColors.green
                        : FlickColors.muted),
                const SizedBox(width: 6),
                Text(_connected ? 'Chat en direct' : 'Connexion…',
                    style: FlickTypography.caption
                        .copyWith(color: FlickColors.muted)),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: ListView.builder(
              controller: _scroll,
              padding: const EdgeInsets.all(FlickSpacing.sm),
              itemCount: _messages.length,
              itemBuilder: (_, i) {
                final m = _messages[i];
                if (m.system) {
                  return Center(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: Text('${m.author} — ${m.text}',
                          style: FlickTypography.caption.copyWith(
                              color: FlickColors.muted)),
                    ),
                  );
                }
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 3),
                  child: RichText(
                    text: TextSpan(
                      style: FlickTypography.bodySmall,
                      children: [
                        TextSpan(
                          text: '${m.author} ',
                          style: TextStyle(
                              fontWeight: FontWeight.w700,
                              color: m.mine
                                  ? FlickColors.green
                                  : FlickColors.white),
                        ),
                        TextSpan(text: m.text),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
          const Divider(height: 1),
          Padding(
            padding: const EdgeInsets.all(FlickSpacing.sm),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _controller,
                    textInputAction: TextInputAction.send,
                    onSubmitted: (_) => _send(),
                    decoration: const InputDecoration(
                      hintText: 'Écrire un message…',
                      isDense: true,
                      contentPadding: EdgeInsets.symmetric(
                          horizontal: 12, vertical: 10),
                    ),
                  ),
                ),
                const SizedBox(width: FlickSpacing.sm),
                IconButton.filled(
                  onPressed: _sending ? null : _send,
                  icon: _sending
                      ? const SizedBox(
                          width: 18, height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.send, size: 20),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
