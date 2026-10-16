import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../shared/models/content.dart';
import '../../../shared/widgets/widgets.dart';
import '../content/content_repository.dart';
import '../discovery/discovery_repository.dart';

/// Recherche créateurs / lives / VOD / clips.
/// Requête SERVEUR (?search=) sur /streams, /creators, /videos/feed,
/// /clips/feed + filtre local de sécurité ; loading / empty / error gérés.
class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key, this.initialQuery = ''});

  final String initialQuery;

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  late final TextEditingController _controller;
  String _query = '';
  late Future<_SearchData> _future;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery);
    _query = widget.initialQuery;
    _future = _load();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<_SearchData> _load() async {
    // Repos capturés AVANT tout await (pas de BuildContext après un gap).
    final content = context.read<ContentRepository>();
    final channel = context.read<ChannelRepository>();
    final discovery = context.read<DiscoveryRepository>();
    final q = _query.trim();
    final search = q.isEmpty ? null : q;
    // Recherche côté serveur (?search=) ; en cas d'échec on retombe
    // sur les flux généraux puis filtre local.
    Future<List<LiveStream>> lives() => content
        .streams(search: search)
        .catchError((_) => content.streams().catchError((_) => <LiveStream>[]));
    Future<List<Creator>> creators() => search == null
        ? discovery.emerging().catchError((_) => <Creator>[])
        : channel.search(q).catchError(
            (_) => discovery.emerging().catchError((_) => <Creator>[]));
    final results = await Future.wait([
      lives().catchError((_) => <LiveStream>[]),
      creators().catchError((_) => <Creator>[]),
      content.videosFeed(search: search).catchError((_) => <VideoItem>[]),
      content.clipsFeed(search: search).catchError((_) => <ClipItem>[]),
    ]);
    return _SearchData(
      lives: results[0] as List<LiveStream>,
      creators: results[1] as List<Creator>,
      videos: results[2] as List<VideoItem>,
      clips: results[3] as List<ClipItem>,
    );
  }

  bool _match(String? haystack) {
    final q = _query.trim().toLowerCase();
    if (q.isEmpty) return true;
    return (haystack ?? '').toLowerCase().contains(q);
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(FlickSpacing.md),
          child: TextField(
            controller: _controller,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              hintText: 'Chaînes, jeux, créateurs…',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: _query.isEmpty
                  ? null
                  : IconButton(
                      icon: const Icon(Icons.clear),
                      onPressed: () => setState(() {
                        _controller.clear();
                        _query = '';
                      }),
                    ),
            ),
            onChanged: (v) => setState(() => _query = v),
            // Recherche serveur (?search=) à la validation du clavier.
            onSubmitted: (_) => setState(() => _future = _load()),
          ),
        ),
        Expanded(
          child: FutureBuilder<_SearchData>(
            future: _future,
            builder: (context, snap) {
              if (snap.connectionState == ConnectionState.waiting) {
                return const Padding(
                  padding: EdgeInsets.all(FlickSpacing.md),
                  child: FlickSkeletonList(),
                );
              }
              if (snap.hasError) {
                return FlickErrorState(
                  message: 'Recherche indisponible : ${snap.error}',
                  onRetry: () => setState(() => _future = _load()),
                );
              }
              final data = snap.data!;
              final lives = data.lives
                  .where((s) =>
                      _match(s.title) ||
                      _match(s.creatorName) ||
                      _match(s.categoryName))
                  .toList();
              final creators = data.creators
                  .where((c) => _match(c.name) || _match(c.categoryName))
                  .toList();
              final videos = data.videos
                  .where((v) => _match(v.title) || _match(v.creatorName))
                  .toList();
              final clips = data.clips
                  .where((c) => _match(c.title) || _match(c.creatorName))
                  .toList();
              if (lives.isEmpty &&
                  creators.isEmpty &&
                  videos.isEmpty &&
                  clips.isEmpty) {
                return FlickEmptyState(
                    message: _query.trim().isEmpty
                        ? 'Tapez pour rechercher'
                        : 'Aucun résultat pour « $_query »');
              }
              return ListView(
                padding:
                    const EdgeInsets.symmetric(horizontal: FlickSpacing.md),
                children: [
                  if (lives.isNotEmpty) ...[
                    const FlickSectionHeader(title: 'Lives'),
                    ...lives.map((s) => _row(
                          context,
                          title: s.title,
                          subtitle: s.creatorName ?? s.creatorSlug ?? '',
                          trailing: const FlickLiveBadge(),
                          onTap: () => Navigator.of(context)
                              .pushNamed('/live', arguments: s.id),
                        )),
                  ],
                  if (creators.isNotEmpty) ...[
                    const FlickSectionHeader(title: 'Créateurs'),
                    ...creators.map((c) => _row(
                          context,
                          title: c.name,
                          subtitle: c.categoryName ?? '',
                          leading: FlickAvatar(
                              name: c.name, url: c.avatarUrl, radius: 18),
                          onTap: () => Navigator.of(context)
                              .pushNamed('/channel', arguments: c.slug),
                        )),
                  ],
                  if (videos.isNotEmpty) ...[
                    const FlickSectionHeader(title: 'Vidéos'),
                    ...videos.map((v) => _row(
                          context,
                          title: v.title,
                          subtitle: v.creatorName ?? '',
                          onTap: () => Navigator.of(context)
                              .pushNamed('/video', arguments: v.id),
                        )),
                  ],
                  if (clips.isNotEmpty) ...[
                    const FlickSectionHeader(title: 'Clips'),
                    ...clips.map((c) => _row(
                          context,
                          title: c.title,
                          subtitle: c.creatorName ?? '',
                          onTap: () => Navigator.of(context)
                              .pushNamed('/clip', arguments: c.id),
                        )),
                  ],
                  const SizedBox(height: FlickSpacing.xl),
                ],
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _row(BuildContext context,
      {required String title,
      required String subtitle,
      Widget? leading,
      Widget? trailing,
      required VoidCallback onTap}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: FlickSpacing.sm),
      child: FlickCard(
        onTap: onTap,
        child: Row(
          children: [
            leading ?? const SizedBox.shrink(),
            if (leading != null)
              const SizedBox(width: FlickSpacing.sm),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: FlickTypography.bodySmall
                          .copyWith(fontWeight: FontWeight.w600)),
                  if (subtitle.isNotEmpty) ...[
                    Text(subtitle,
                        style: FlickTypography.caption
                            .copyWith(color: FlickColors.muted)),
                  ],
                ],
              ),
            ),
            trailing ?? const SizedBox.shrink(),
          ],
        ),
      ),
    );
  }
}

class _SearchData {
  _SearchData(
      {required this.lives,
      required this.creators,
      required this.videos,
      required this.clips});

  final List<LiveStream> lives;
  final List<Creator> creators;
  final List<VideoItem> videos;
  final List<ClipItem> clips;
}
