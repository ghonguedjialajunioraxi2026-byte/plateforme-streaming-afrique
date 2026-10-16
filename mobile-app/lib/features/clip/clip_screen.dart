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

/// Clip : player vertical/horizontal + créateur + partage + chaîne.
class ClipScreen extends StatefulWidget {
  const ClipScreen({super.key, required this.clipId});

  final int clipId;

  @override
  State<ClipScreen> createState() => _ClipScreenState();
}

class _ClipScreenState extends State<ClipScreen> {
  late Future<ClipItem> _future;
  VideoPlayerController? _player;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<ClipItem> _load() async {
    final prefsLevel = context.read<AppPrefs>().dataSaver;
    final repo = context.read<ContentRepository>();
    final clip = await repo.clip(widget.clipId);
    final playback = await repo.clipPlayback(widget.clipId);
    final url = pickSource(
      hlsUrl: (playback['hlsUrl'] ?? clip.hlsUrl)?.toString(),
      playbackUrl:
          (playback['playbackUrl'] ?? clip.playbackUrl)?.toString(),
      level: prefsLevel,
    );
    if (url != null && url.isNotEmpty) {
      try {
        final controller =
            VideoPlayerController.networkUrl(Uri.parse(url));
        await controller.initialize();
        await controller.play();
        if (!mounted) {
          await controller.dispose();
        } else {
          await _player?.dispose();
          setState(() => _player = controller);
        }
      } catch (_) {}
    }
    return clip;
  }

  @override
  void dispose() {
    _player?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Clip')),
      body: FutureBuilder<ClipItem>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const FlickLoading(label: 'Chargement du clip…');
          }
          if (snap.hasError || !snap.hasData) {
            return FlickErrorState(
              message: snap.error is ApiException
                  ? (snap.error as ApiException).message
                  : 'Clip introuvable',
              onRetry: () => setState(() => _future = _load()),
            );
          }
          final clip = snap.data!;
          final p = _player;
          return ListView(
            children: [
              AspectRatio(
                aspectRatio: 16 / 9,
                child: Container(
                  color: Colors.black,
                  child: p == null || !p.value.isInitialized
                      ? const FlickLoading(label: 'Préparation…')
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
                    Text(clip.title, style: FlickTypography.title),
                    const SizedBox(height: 4),
                    Text(
                        '${clip.creatorName ?? ''} · ${formatCount(clip.viewCount)} vues',
                        style: FlickTypography.caption
                            .copyWith(color: FlickColors.muted)),
                    const SizedBox(height: FlickSpacing.md),
                    Row(
                      children: [
                        Expanded(
                          child: FlickButton(
                            label: 'Chaîne',
                            icon: Icons.person,
                            secondary: true,
                            onPressed: clip.creatorSlug == null
                                ? null
                                : () => Navigator.of(context).pushNamed(
                                    '/channel',
                                    arguments: clip.creatorSlug),
                          ),
                        ),
                        const SizedBox(width: FlickSpacing.sm),
                        Expanded(
                          child: FlickButton(
                            label: 'Partager',
                            icon: Icons.share,
                            secondary: true,
                            onPressed: () => shareContent(context,
                                kind: 'clip',
                                id: clip.shareSlug ?? '${clip.id}',
                                title: clip.title),
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
