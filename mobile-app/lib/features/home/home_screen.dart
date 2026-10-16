import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/connectivity/connectivity_service.dart';
import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../core/utils/format.dart';
import '../../../shared/models/content.dart';
import '../../../shared/widgets/widgets.dart';
import '../content/content_repository.dart';
import '../discovery/discovery_repository.dart';

/// Accueil mobile : lives, créateurs, catégories, VOD, clips.
/// Données backend réelles ; offline/erreur gérés, jamais d'écran blanc.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late Future<_HomeData> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_HomeData> _load() async {
    final ctx = context;
    final discovery = ctx.read<DiscoveryRepository>();
    final content = ctx.read<ContentRepository>();
    final results = await Future.wait([
      discovery.trending().catchError((_) => <LiveStream>[]),
      discovery.emerging().catchError((_) => <Creator>[]),
      content.videosFeed().catchError((_) => <VideoItem>[]),
      content.clipsFeed().catchError((_) => <ClipItem>[]),
    ]);
    return _HomeData(
      lives: results[0] as List<LiveStream>,
      creators: results[1] as List<Creator>,
      videos: results[2] as List<VideoItem>,
      clips: results[3] as List<ClipItem>,
    );
  }

  @override
  Widget build(BuildContext context) {
    final online = context.watch<ConnectivityService>().isOnline;
    return RefreshIndicator(
      onRefresh: () async => setState(() => _future = _load()),
      child: ListView(
        padding: const EdgeInsets.all(FlickSpacing.md),
        children: [
          if (!online) const FlickOfflineBar(message: 'Hors ligne — contenu récent affiché'),
          FutureBuilder<_HomeData>(
            future: _future,
            builder: (context, snap) {
              if (snap.connectionState == ConnectionState.waiting) {
                return const FlickSkeletonList(items: 6);
              }
              if (snap.hasError) {
                return FlickErrorState(
                  message: 'Impossible de charger : ${snap.error}',
                  onRetry: () => setState(() => _future = _load()),
                );
              }
              final data = snap.data!;
              if (data.isEmpty) {
                return const FlickEmptyState(
                    message: 'Aucun contenu pour le moment');
              }
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (data.lives.isNotEmpty) ...[
                    FlickSectionHeader(
                      title: 'En direct',
                      onSeeAll: () =>
                          Navigator.of(context).pushNamed('/explore'),
                    ),
                    const SizedBox(height: FlickSpacing.sm),
                    SizedBox(
                      height: 210,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: data.lives.length,
                        separatorBuilder: (_, _) =>
                            const SizedBox(width: FlickSpacing.sm),
                        itemBuilder: (_, i) =>
                            _LiveCard(live: data.lives[i]),
                      ),
                    ),
                    const SizedBox(height: FlickSpacing.lg),
                  ],
                  if (data.creators.isNotEmpty) ...[
                    const FlickSectionHeader(title: 'Créateurs à suivre'),
                    const SizedBox(height: FlickSpacing.sm),
                    SizedBox(
                      height: 96,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: data.creators.length,
                        separatorBuilder: (_, _) =>
                            const SizedBox(width: FlickSpacing.md),
                        itemBuilder: (_, i) {
                          final c = data.creators[i];
                          return GestureDetector(
                            onTap: () => Navigator.of(context).pushNamed(
                                '/channel', arguments: c.slug),
                            child: Column(
                              children: [
                                FlickAvatar(
                                    name: c.name,
                                    url: c.avatarUrl,
                                    radius: 28,
                                    showDot: c.isLive),
                                const SizedBox(height: 4),
                                SizedBox(
                                  width: 72,
                                  child: Text(c.name,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      textAlign: TextAlign.center,
                                      style: FlickTypography.caption),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
                    ),
                    const SizedBox(height: FlickSpacing.lg),
                  ],
                  if (data.videos.isNotEmpty) ...[
                    const FlickSectionHeader(title: 'Vidéos'),
                    const SizedBox(height: FlickSpacing.sm),
                    ...data.videos.take(5).map((v) => Padding(
                          padding: const EdgeInsets.only(
                              bottom: FlickSpacing.sm),
                          child: _VideoTile(video: v),
                        )),
                    const SizedBox(height: FlickSpacing.lg),
                  ],
                  if (data.clips.isNotEmpty) ...[
                    const FlickSectionHeader(title: 'Clips'),
                    const SizedBox(height: FlickSpacing.sm),
                    ...data.clips.take(5).map((c) => Padding(
                          padding: const EdgeInsets.only(
                              bottom: FlickSpacing.sm),
                          child: _ClipTile(clip: c),
                        )),
                  ],
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _HomeData {
  _HomeData(
      {required this.lives,
      required this.creators,
      required this.videos,
      required this.clips});

  final List<LiveStream> lives;
  final List<Creator> creators;
  final List<VideoItem> videos;
  final List<ClipItem> clips;

  bool get isEmpty =>
      lives.isEmpty && creators.isEmpty && videos.isEmpty && clips.isEmpty;
}

class _LiveCard extends StatelessWidget {
  const _LiveCard({required this.live});

  final LiveStream live;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () =>
          Navigator.of(context).pushNamed('/live', arguments: live.id),
      child: SizedBox(
        width: 170,
        child: FlickCard(
          padding: EdgeInsets.zero,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Stack(
                children: [
                  ClipRRect(
                    borderRadius: const BorderRadius.vertical(
                        top: Radius.circular(FlickRadius.md)),
                    child: live.thumbnailUrl == null
                        ? Container(
                            height: 110, color: FlickColors.surface2,
                            child: const Center(
                                child: Icon(Icons.live_tv,
                                    color: FlickColors.muted)))
                        : CachedNetworkImage(
                            imageUrl: live.thumbnailUrl!,
                            height: 110, width: 170, fit: BoxFit.cover,
                            errorWidget: (_, _, _) => Container(
                                height: 110, color: FlickColors.surface2),
                          ),
                  ),
                  const Positioned(
                      top: 6, left: 6, child: FlickLiveBadge()),
                  Positioned(
                    bottom: 6, right: 6,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                          color: Colors.black54,
                          borderRadius:
                              BorderRadius.circular(FlickRadius.sm)),
                      child: FlickViewerCount(
                          count: live.viewerCount, color: Colors.white),
                    ),
                  ),
                ],
              ),
              Padding(
                padding: const EdgeInsets.all(FlickSpacing.sm),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(live.title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: FlickTypography.bodySmall.copyWith(
                            fontWeight: FontWeight.w600)),
                    const SizedBox(height: 2),
                    Text(live.creatorName ?? live.creatorSlug ?? '',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: FlickTypography.caption.copyWith(
                            color: FlickColors.muted)),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _VideoTile extends StatelessWidget {
  const _VideoTile({required this.video});

  final VideoItem video;

  @override
  Widget build(BuildContext context) {
    return FlickCard(
      onTap: () =>
          Navigator.of(context).pushNamed('/video', arguments: video.id),
      padding: EdgeInsets.zero,
      child: Row(
        children: [
          ClipRRect(
            borderRadius: const BorderRadius.horizontal(
                left: Radius.circular(FlickRadius.md)),
            child: video.thumbnailUrl == null
                ? Container(
                    width: 120, height: 72, color: FlickColors.surface2,
                    child: const Icon(Icons.play_circle_outline,
                        color: FlickColors.muted))
                : CachedNetworkImage(
                    imageUrl: video.thumbnailUrl!,
                    width: 120, height: 72, fit: BoxFit.cover,
                    errorWidget: (_, _, _) => Container(
                        width: 120,
                        height: 72,
                        color: FlickColors.surface2),
                  ),
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.all(FlickSpacing.sm),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(video.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: FlickTypography.bodySmall
                          .copyWith(fontWeight: FontWeight.w600)),
                  const SizedBox(height: 4),
                  Text(
                      '${video.creatorName ?? ''} · ${formatCount(video.viewCount)} vues',
                      style: FlickTypography.caption
                          .copyWith(color: FlickColors.muted)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ClipTile extends StatelessWidget {
  const _ClipTile({required this.clip});

  final ClipItem clip;

  @override
  Widget build(BuildContext context) {
    return FlickCard(
      onTap: () =>
          Navigator.of(context).pushNamed('/clip', arguments: clip.id),
      child: Row(
        children: [
          const Icon(Icons.movie_outlined, color: FlickColors.green),
          const SizedBox(width: FlickSpacing.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(clip.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: FlickTypography.bodySmall
                        .copyWith(fontWeight: FontWeight.w600)),
                Text(
                    '${clip.creatorName ?? ''} · ${formatCount(clip.viewCount)} vues',
                    style: FlickTypography.caption
                        .copyWith(color: FlickColors.muted)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
