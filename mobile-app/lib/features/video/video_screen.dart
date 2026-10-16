import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:video_player/video_player.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/storage/app_prefs.dart';
import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../core/utils/format.dart';
import '../../../shared/models/content.dart';
import '../../../shared/utils/share_content.dart';
import '../../../shared/widgets/widgets.dart';
import '../content/content_repository.dart';
import '../live/live_screen.dart' show pickSource;

/// VOD : player (playbackUrl/hlsUrl serveur) + infos + actions.
class VideoScreen extends StatefulWidget {
  const VideoScreen({super.key, required this.videoId});

  final int videoId;

  @override
  State<VideoScreen> createState() => _VideoScreenState();
}

class _VideoScreenState extends State<VideoScreen> {
  late Future<_VideoData> _future;
  VideoPlayerController? _player;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_VideoData> _load() async {
    final repo = context.read<ContentRepository>();
    final video = await repo.video(widget.videoId);
    final playback = await repo.videoPlayback(widget.videoId);
    await _initPlayer(
      (playback['hlsUrl'] ?? video.hlsUrl)?.toString(),
      (playback['playbackUrl'] ?? video.playbackUrl)?.toString(),
    );
    return _VideoData(video: video);
  }

  Future<void> _initPlayer(String? hlsUrl, String? playbackUrl) async {
    final url = pickSource(
        hlsUrl: hlsUrl,
        playbackUrl: playbackUrl,
        level: context.read<AppPrefs>().dataSaver);
    if (url == null || url.isEmpty) return;
    try {
      final controller = VideoPlayerController.networkUrl(Uri.parse(url));
      await controller.initialize();
      if (!mounted) {
        await controller.dispose();
        return;
      }
      await _player?.dispose();
      setState(() => _player = controller);
    } catch (_) {}
  }

  @override
  void dispose() {
    _player?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Vidéo')),
      body: FutureBuilder<_VideoData>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const FlickLoading(label: 'Chargement de la vidéo…');
          }
          if (snap.hasError || !snap.hasData) {
            return FlickErrorState(
              message: snap.error is ApiException
                  ? (snap.error as ApiException).message
                  : 'Vidéo introuvable',
              onRetry: () => setState(() => _future = _load()),
            );
          }
          final video = snap.data!.video;
          final p = _player;
          return ListView(
            children: [
              AspectRatio(
                aspectRatio: 16 / 9,
                child: Container(
                  color: Colors.black,
                  child: p == null || !p.value.isInitialized
                      ? (video.thumbnailUrl == null
                          ? const FlickLoading(label: 'Préparation…')
                          : CachedNetworkImage(
                              imageUrl: video.thumbnailUrl!,
                              fit: BoxFit.cover,
                              errorWidget: (_, _, _) =>
                                  const FlickLoading(label: 'Préparation…'),
                            ))
                      : Stack(
                          alignment: Alignment.center,
                          children: [
                            VideoPlayer(p),
                            GestureDetector(
                              onTap: () => setState(() => p.value.isPlaying
                                  ? p.pause()
                                  : p.play()),
                              child:
                                  Container(color: Colors.transparent),
                            ),
                            if (!p.value.isPlaying)
                              const Icon(Icons.play_circle_fill,
                                  size: 64, color: Colors.white70),
                          ],
                        ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(FlickSpacing.md),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(video.title, style: FlickTypography.title),
                    const SizedBox(height: 4),
                    Text(
                        '${video.creatorName ?? ''} · ${formatCount(video.viewCount)} vues'
                        '${video.durationSeconds != null ? ' · ${formatDuration(video.durationSeconds!)}' : ''}',
                        style: FlickTypography.caption
                            .copyWith(color: FlickColors.muted)),
                    if (video.description?.isNotEmpty == true) ...[
                      const SizedBox(height: FlickSpacing.sm),
                      Text(video.description!,
                          style: FlickTypography.bodySmall),
                    ],
                    const SizedBox(height: FlickSpacing.md),
                    Row(
                      children: [
                        Expanded(
                          child: FlickButton(
                            label: 'Chaîne',
                            icon: Icons.person,
                            secondary: true,
                            onPressed: video.creatorSlug == null
                                ? null
                                : () => Navigator.of(context).pushNamed(
                                    '/channel',
                                    arguments: video.creatorSlug),
                          ),
                        ),
                        const SizedBox(width: FlickSpacing.sm),
                        Expanded(
                          child: FlickButton(
                            label: 'Partager',
                            icon: Icons.share,
                            secondary: true,
                            onPressed: () => shareContent(context,
                                kind: 'video',
                                id: '${video.id}',
                                title: video.title),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _VideoData {
  _VideoData({required this.video});

  final VideoItem video;
}
