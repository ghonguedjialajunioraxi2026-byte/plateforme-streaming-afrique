import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/connectivity/connectivity_service.dart';
import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../shared/models/content.dart';
import '../../../shared/widgets/widgets.dart';
import '../discovery/discovery_repository.dart';

/// Explorer : tendances, émergents, catégories (données réelles).
class ExploreScreen extends StatefulWidget {
  const ExploreScreen({super.key});

  @override
  State<ExploreScreen> createState() => _ExploreScreenState();
}

class _ExploreScreenState extends State<ExploreScreen> {
  late Future<_ExploreData> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_ExploreData> _load() async {
    final repo = context.read<DiscoveryRepository>();
    final trending = await repo.trending().catchError((_) => <LiveStream>[]);
    final emerging = await repo.emerging().catchError((_) => <Creator>[]);
    return _ExploreData(trending: trending, emerging: emerging);
  }

  @override
  Widget build(BuildContext context) {
    final online = context.watch<ConnectivityService>().isOnline;
    return RefreshIndicator(
      onRefresh: () async => setState(() => _future = _load()),
      child: ListView(
        padding: const EdgeInsets.all(FlickSpacing.md),
        children: [
          if (!online) const FlickOfflineBar(message: 'Hors ligne'),
          FutureBuilder<_ExploreData>(
            future: _future,
            builder: (context, snap) {
              if (snap.connectionState == ConnectionState.waiting) {
                return const FlickSkeletonList();
              }
              if (snap.hasError) {
                return FlickErrorState(
                  message: 'Exploration indisponible : ${snap.error}',
                  onRetry: () => setState(() => _future = _load()),
                );
              }
              final data = snap.data!;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const FlickSectionHeader(title: 'Tendances'),
                  const SizedBox(height: FlickSpacing.sm),
                  if (data.trending.isEmpty)
                    const FlickEmptyState(message: 'Aucune tendance actuellement')
                  else
                    ...data.trending.map((s) => Padding(
                          padding: const EdgeInsets.only(
                              bottom: FlickSpacing.sm),
                          child: FlickCard(
                            onTap: () => Navigator.of(context)
                                .pushNamed('/live', arguments: s.id),
                            child: Row(
                              children: [
                                const FlickLiveBadge(),
                                const SizedBox(width: FlickSpacing.sm),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(s.title,
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: FlickTypography.bodySmall
                                              .copyWith(
                                                  fontWeight:
                                                      FontWeight.w600)),
                                      Text(
                                          s.creatorName ??
                                              s.creatorSlug ??
                                              '',
                                          style: FlickTypography.caption
                                              .copyWith(
                                                  color: FlickColors.muted)),
                                    ],
                                  ),
                                ),
                                FlickViewerCount(count: s.viewerCount),
                              ],
                            ),
                          ),
                        )),
                  const SizedBox(height: FlickSpacing.lg),
                  const FlickSectionHeader(title: 'Créateurs émergents'),
                  const SizedBox(height: FlickSpacing.sm),
                  if (data.emerging.isEmpty)
                    const FlickEmptyState(message: 'Aucun créateur émergent')
                  else
                    ...data.emerging.map((c) => Padding(
                          padding: const EdgeInsets.only(
                              bottom: FlickSpacing.sm),
                          child: FlickCard(
                            onTap: () => Navigator.of(context).pushNamed(
                                '/channel', arguments: c.slug),
                            child: Row(
                              children: [
                                FlickAvatar(
                                    name: c.name,
                                    url: c.avatarUrl,
                                    showDot: c.isLive),
                                const SizedBox(width: FlickSpacing.sm),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Row(
                                        children: [
                                          Flexible(
                                            child: Text(c.name,
                                                maxLines: 1,
                                                overflow:
                                                    TextOverflow.ellipsis,
                                                style: FlickTypography
                                                    .bodySmall
                                                    .copyWith(
                                                        fontWeight:
                                                            FontWeight
                                                                .w600)),
                                          ),
                                          if (c.verified) ...[
                                            const SizedBox(width: 4),
                                            const Icon(
                                                Icons.verified,
                                                size: 14,
                                                color: FlickColors.green),
                                          ],
                                        ],
                                      ),
                                      if (c.categoryName != null)
                                        Text(c.categoryName!,
                                            style: FlickTypography.caption
                                                .copyWith(
                                                    color:
                                                        FlickColors.muted)),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        )),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _ExploreData {
  _ExploreData({required this.trending, required this.emerging});

  final List<LiveStream> trending;
  final List<Creator> emerging;
}
