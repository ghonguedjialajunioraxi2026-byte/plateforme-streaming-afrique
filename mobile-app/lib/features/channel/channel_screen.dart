import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../core/utils/format.dart';
import '../../../shared/models/content.dart';
import '../../../shared/utils/share_content.dart';
import '../../../shared/widgets/widgets.dart';
import '../auth/auth_controller.dart';
import '../content/content_repository.dart';

/// Chaîne créateur : banner, avatar, vérifié, follow, lives/VOD/clips.
class ChannelScreen extends StatefulWidget {
  const ChannelScreen({super.key, required this.slug});

  final String slug;

  @override
  State<ChannelScreen> createState() => _ChannelScreenState();
}

class _ChannelScreenState extends State<ChannelScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  late Future<_ChannelData> _future;
  bool _following = false;
  bool _followBusy = false;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this);
    _future = _load();
  }

  Future<_ChannelData> _load() async {
    final repo = context.read<ChannelRepository>();
    final creator = await repo.bySlug(widget.slug);
    final videos =
        await repo.videos(creator.id).catchError((_) => <VideoItem>[]);
    final clips =
        await repo.clips(creator.id).catchError((_) => <ClipItem>[]);
    await _loadFollow(creator);
    return _ChannelData(creator: creator, videos: videos, clips: clips);
  }

  Future<void> _loadFollow(Creator creator) async {
    final auth = context.read<AuthController>();
    if (!auth.isAuthenticated) return;
    try {
      final v = await context.read<ChannelRepository>().isFollowing(
          followerId: auth.user!.id, channelId: creator.id);
      if (mounted) setState(() => _following = v);
    } catch (_) {}
  }

  Future<void> _toggleFollow(Creator creator) async {
    final auth = context.read<AuthController>();
    if (!auth.isAuthenticated) {
      Navigator.of(context).pushNamed('/login');
      return;
    }
    setState(() => _followBusy = true);
    try {
      final repo = context.read<ChannelRepository>();
      if (_following) {
        await repo.unfollow(
            followerId: auth.user!.id, channelId: creator.id);
      } else {
        await repo.follow(
            followerId: auth.user!.id, channelId: creator.id);
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
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Chaîne')),
      body: FutureBuilder<_ChannelData>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const FlickLoading(label: 'Chargement de la chaîne…');
          }
          if (snap.hasError || !snap.hasData) {
            return FlickErrorState(
              message: 'Chaîne introuvable',
              onRetry: () => setState(() => _future = _load()),
            );
          }
          final creator = snap.data!.creator;
          return NestedScrollView(
            headerSliverBuilder: (_, _) => [
              SliverToBoxAdapter(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (creator.bannerUrl != null)
                      CachedNetworkImage(
                        imageUrl: creator.bannerUrl!,
                        height: 130,
                        width: double.infinity,
                        fit: BoxFit.cover,
                        errorWidget: (_, _, _) =>
                            Container(height: 130, color: FlickColors.surface2),
                      )
                    else
                      Container(height: 90, color: FlickColors.surface2),
                    Padding(
                      padding: const EdgeInsets.all(FlickSpacing.md),
                      child: Row(
                        children: [
                          FlickAvatar(
                              name: creator.name,
                              url: creator.avatarUrl,
                              radius: 30,
                              showDot: creator.isLive),
                          const SizedBox(width: FlickSpacing.md),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Flexible(
                                      child: Text(creator.name,
                                          style: FlickTypography.title),
                                    ),
                                    if (creator.verified) ...[
                                      const SizedBox(width: 4),
                                      const Icon(Icons.verified,
                                          size: 16,
                                          color: FlickColors.green),
                                    ],
                                  ],
                                ),
                                Text(
                                  '${formatCount(creator.followers)} abonnés'
                                  '${creator.categoryName != null ? ' · ${creator.categoryName}' : ''}',
                                  style: FlickTypography.caption.copyWith(
                                      color: FlickColors.muted),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(
                          horizontal: FlickSpacing.md),
                      child: Row(
                        children: [
                          Expanded(
                            child: FlickButton(
                              label: _following ? 'Suivi' : 'Suivre',
                              secondary: _following,
                              onPressed: _followBusy
                                  ? null
                                  : () => _toggleFollow(creator),
                            ),
                          ),
                          const SizedBox(width: FlickSpacing.sm),
                          IconButton.outlined(
                            tooltip: 'Partager la chaîne',
                            icon: const Icon(Icons.share),
                            onPressed: () => shareContent(context,
                                kind: 'channel',
                                id: creator.slug,
                                title: creator.name),
                          ),
                        ],
                      ),
                    ),
                    if (creator.description?.isNotEmpty == true)
                      Padding(
                        padding: const EdgeInsets.all(FlickSpacing.md),
                        child: Text(creator.description!,
                            style: FlickTypography.bodySmall.copyWith(
                                color: FlickColors.muted)),
                      ),
                  ],
                ),
              ),
              SliverPersistentHeader(
                pinned: true,
                delegate: _TabDelegate(
                  TabBar(
                    controller: _tabs,
                    tabs: const [
                      Tab(text: 'VOD'),
                      Tab(text: 'Clips'),
                      Tab(text: 'Infos'),
                    ],
                  ),
                ),
              ),
            ],
            body: TabBarView(
              controller: _tabs,
              children: [
                _mediaList(
                    snap.data!.videos
                        .map((v) => (v.title,
                            '${formatCount(v.viewCount)} vues',
                            () => Navigator.of(context)
                                .pushNamed('/video', arguments: v.id)))
                        .toList(),
                    'Aucune VOD'),
                _mediaList(
                    snap.data!.clips
                        .map((c) => (c.title,
                            '${formatCount(c.viewCount)} vues',
                            () => Navigator.of(context)
                                .pushNamed('/clip', arguments: c.id)))
                        .toList(),
                    'Aucun clip'),
                Padding(
                  padding: const EdgeInsets.all(FlickSpacing.md),
                  child: Text(
                      creator.description?.isNotEmpty == true
                          ? creator.description!
                          : 'Aucune description.',
                      style: FlickTypography.bodySmall),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _mediaList(List<(String, String, VoidCallback)> items, String empty) {
    if (items.isEmpty) {
      return FlickEmptyState(message: empty);
    }
    return ListView.separated(
      padding: const EdgeInsets.all(FlickSpacing.md),
      itemCount: items.length,
      separatorBuilder: (_, _) =>
          const SizedBox(height: FlickSpacing.sm),
      itemBuilder: (_, i) => FlickCard(
        onTap: items[i].$3,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(items[i].$1,
                style: FlickTypography.bodySmall
                    .copyWith(fontWeight: FontWeight.w600)),
            Text(items[i].$2,
                style: FlickTypography.caption
                    .copyWith(color: FlickColors.muted)),
          ],
        ),
      ),
    );
  }
}

class _ChannelData {
  _ChannelData(
      {required this.creator, required this.videos, required this.clips});

  final Creator creator;
  final List<VideoItem> videos;
  final List<ClipItem> clips;
}

class _TabDelegate extends SliverPersistentHeaderDelegate {
  _TabDelegate(this.tabBar);

  final TabBar tabBar;

  @override
  Widget build(
          BuildContext context, double shrinkOffset, bool overlapsContent) =>
      Container(color: FlickColors.black, child: tabBar);

  @override
  double get maxExtent => 48;

  @override
  double get minExtent => 48;

  @override
  bool shouldRebuild(covariant SliverPersistentHeaderDelegate oldDelegate) =>
      false;
}
