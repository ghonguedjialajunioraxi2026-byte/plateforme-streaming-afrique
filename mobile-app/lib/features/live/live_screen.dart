import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:video_player/video_player.dart';

import '../../../core/connectivity/connectivity_service.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/storage/app_prefs.dart';
import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../shared/models/content.dart';
import '../../../shared/utils/share_content.dart';
import '../../../shared/widgets/widgets.dart';
import '../auth/auth_controller.dart';
import '../content/content_repository.dart';
import 'widgets/chat_sheet.dart';
import 'widgets/gift_selector.dart';

/// Choisit la source selon le niveau data saver (serveur = vérité,
/// le player adaptatif HLS fait le reste ; Éco préfère le flux direct).
String? pickSource({String? hlsUrl, String? playbackUrl, required String level}) {
  if (level == 'eco') return playbackUrl ?? hlsUrl;
  return hlsUrl ?? playbackUrl;
}

/// Live : player HLS (infra existante) + titre/créateur/follow/chat/cadeaux.
/// Layout vertical mobile : player → infos → actions → chat.
class LiveScreen extends StatefulWidget {
  const LiveScreen({super.key, required this.streamId});

  final int streamId;

  @override
  State<LiveScreen> createState() => _LiveScreenState();
}

class _LiveScreenState extends State<LiveScreen> {
  late Future<LiveStream> _future;
  VideoPlayerController? _player;
  bool _playerError = false;
  bool _following = false;
  bool _followBusy = false;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<LiveStream> _load() async {
    final stream = await context
        .read<ContentRepository>()
        .stream(widget.streamId);
    await _initPlayer(stream);
    await _loadFollow(stream);
    return stream;
  }

  Future<void> _initPlayer(LiveStream stream) async {
    final prefs = context.read<AppPrefs>();
    final url = pickSource(
      hlsUrl: null, // StreamDto.playbackUrl = HLS quand LIVE
      playbackUrl: stream.playbackUrl,
      level: prefs.dataSaver,
    );
    if (url == null || url.isEmpty) return;
    try {
      final controller = VideoPlayerController.networkUrl(Uri.parse(url));
      await controller.initialize();
      await controller.play();
      if (!mounted) {
        await controller.dispose();
        return;
      }
      await _player?.dispose();
      setState(() {
        _player = controller;
        _playerError = false;
      });
    } catch (_) {
      if (mounted) setState(() => _playerError = true);
    }
  }

  Future<void> _loadFollow(LiveStream stream) async {
    final auth = context.read<AuthController>();
    if (!auth.isAuthenticated || stream.creatorId == null) return;
    try {
      final v = await context.read<ChannelRepository>().isFollowing(
          followerId: auth.user!.id, channelId: stream.creatorId!);
      if (mounted) setState(() => _following = v);
    } catch (_) {}
  }

  Future<void> _toggleFollow(LiveStream stream) async {
    final auth = context.read<AuthController>();
    if (!auth.isAuthenticated) {
      Navigator.of(context).pushNamed('/login');
      return;
    }
    if (stream.creatorId == null) return;
    setState(() => _followBusy = true);
    final repo = context.read<ChannelRepository>();
    try {
      if (_following) {
        await repo.unfollow(
            followerId: auth.user!.id, channelId: stream.creatorId!);
      } else {
        await repo.follow(
            followerId: auth.user!.id, channelId: stream.creatorId!);
      }
      setState(() => _following = !_following);
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => _followBusy = false);
    }
  }

  @override
  void dispose() {
    _player?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final online = context.watch<ConnectivityService>().isOnline;
    return Scaffold(
      appBar: AppBar(title: const Text('Direct')),
      body: FutureBuilder<LiveStream>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const FlickLoading(label: 'Connexion au direct…');
          }
          if (snap.hasError || !snap.hasData) {
            return FlickErrorState(
              message: snap.hasError && snap.error is ApiException
                  ? (snap.error as ApiException).message
                  : 'Direct introuvable ou terminé',
              onRetry: () => setState(() => _future = _load()),
            );
          }
          final stream = snap.data!;
          return RefreshIndicator(
            onRefresh: () async => setState(() => _future = _load()),
            child: ListView(
              children: [
                if (!online)
                  const FlickOfflineBar(message: 'Hors ligne — reconnexion…'),
                _PlayerArea(
                    player: _player,
                    error: _playerError,
                    live: stream.isLive),
                Padding(
                  padding: const EdgeInsets.all(FlickSpacing.md),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          if (stream.isLive) const FlickLiveBadge(),
                          const SizedBox(width: FlickSpacing.sm),
                          Expanded(
                            child: FlickViewerCount(
                                count: stream.viewerCount),
                          ),
                        ],
                      ),
                      const SizedBox(height: FlickSpacing.sm),
                      Text(stream.title, style: FlickTypography.title),
                      const SizedBox(height: FlickSpacing.sm),
                      InkWell(
                        onTap: stream.creatorSlug == null
                            ? null
                            : () => Navigator.of(context).pushNamed(
                                '/channel',
                                arguments: stream.creatorSlug),
                        child: Row(
                          children: [
                            FlickAvatar(
                                name: stream.creatorName,
                                radius: 18,
                                showDot: stream.isLive),
                            const SizedBox(width: FlickSpacing.sm),
                            Expanded(
                              child: Text(
                                  stream.creatorName ??
                                      stream.creatorSlug ??
                                      'Créateur',
                                  style: FlickTypography.bodySmall.copyWith(
                                      fontWeight: FontWeight.w600)),
                            ),
                            if (stream.creatorId != null)
                              FlickButton(
                                label:
                                    _following ? 'Suivi' : 'Suivre',
                                secondary: _following,
                                onPressed: _followBusy
                                    ? null
                                    : () => _toggleFollow(stream),
                              ),
                          ],
                        ),
                      ),
                      if (stream.description?.isNotEmpty == true) ...[
                        const SizedBox(height: FlickSpacing.sm),
                        Text(stream.description!,
                            style: FlickTypography.bodySmall.copyWith(
                                color: FlickColors.muted)),
                      ],
                      const SizedBox(height: FlickSpacing.md),
                      Row(
                        children: [
                          Expanded(
                            child: FlickButton(
                              label: 'Cadeau',
                              icon: Icons.card_giftcard,
                              secondary: true,
                              onPressed: () => showModalBottomSheet(
                                context: context,
                                builder: (_) => GiftSelector(
                                    streamId: stream.id),
                              ),
                            ),
                          ),
                          const SizedBox(width: FlickSpacing.sm),
                          Expanded(
                            child: FlickButton(
                              label: 'Partager',
                              icon: Icons.share,
                              secondary: true,
                              onPressed: () => shareContent(
                                  context,
                                  kind: 'live',
                                  id: '${stream.id}',
                                  title: stream.title),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: FlickSpacing.md),
                      const FlickSectionHeader(title: 'Chat'),
                      const SizedBox(height: FlickSpacing.sm),
                      ChatSheet(
                          streamId: stream.id,
                          currentUser:
                              context.watch<AuthController>().user),
                    ],
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _PlayerArea extends StatelessWidget {
  const _PlayerArea(
      {required this.player, required this.error, required this.live});

  final VideoPlayerController? player;
  final bool error;
  final bool live;

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: 16 / 9,
      child: Container(
        color: Colors.black,
        child: Builder(
          builder: (context) {
            if (error) {
              return const FlickErrorState(
                  message: 'Lecture impossible (format/réseau)');
            }
            final p = player;
            if (p == null || !p.value.isInitialized) {
              return const FlickLoading(label: 'Chargement du flux…');
            }
            return Stack(
              alignment: Alignment.center,
              children: [
                VideoPlayer(p),
                GestureDetector(
                  onTap: () => p.value.isPlaying ? p.pause() : p.play(),
                  child: Container(color: Colors.transparent),
                ),
                if (!p.value.isPlaying)
                  const Icon(Icons.play_circle_fill,
                      size: 64, color: Colors.white70),
                if (!live)
                  const Positioned(
                    bottom: 8, right: 8,
                    child: FlickBadge(label: 'Terminé'),
                  ),
              ],
            );
          },
        ),
      ),
    );
  }
}
