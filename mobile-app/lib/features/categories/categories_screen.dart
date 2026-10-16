import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../core/utils/format.dart';
import '../../../shared/models/content.dart';
import '../../../shared/widgets/widgets.dart';
import '../discovery/discovery_repository.dart';

/// Catégories — liste serveur (+ cache 10 min côté repository).
class CategoriesScreen extends StatefulWidget {
  const CategoriesScreen({super.key});

  @override
  State<CategoriesScreen> createState() => _CategoriesScreenState();
}

class _CategoriesScreenState extends State<CategoriesScreen> {
  late Future<List<CategoryItem>> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<List<CategoryItem>> _load() =>
      context.read<CategoriesRepository>().list().catchError(
          (_) => <CategoryItem>[]);

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async {
        final list = await context
            .read<CategoriesRepository>()
            .list(forceRefresh: true)
            .catchError((_) => <CategoryItem>[]);
        setState(() => _future = Future.value(list));
      },
      child: FutureBuilder<List<CategoryItem>>(
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
              message: 'Catégories indisponibles : ${snap.error}',
              onRetry: () => setState(() => _future = _load()),
            );
          }
          final items = snap.data!;
          if (items.isEmpty) {
            return const FlickEmptyState(message: 'Aucune catégorie');
          }
          return GridView.builder(
            padding: const EdgeInsets.all(FlickSpacing.md),
            gridDelegate:
                const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              mainAxisSpacing: FlickSpacing.sm,
              crossAxisSpacing: FlickSpacing.sm,
              childAspectRatio: 1.6,
            ),
            itemCount: items.length,
            itemBuilder: (_, i) {
              final c = items[i];
              return FlickCard(
                onTap: () => Navigator.of(context)
                    .pushNamed('/search', arguments: c.name),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(c.name,
                        textAlign: TextAlign.center,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: FlickTypography.title
                            .copyWith(fontSize: 16)),
                    const SizedBox(height: 4),
                    Text('${formatCount(c.viewers)} spectateurs',
                        style: FlickTypography.caption.copyWith(
                            color: FlickColors.live)),
                  ],
                ),
              );
            },
          );
        },
      ),
    );
  }
}
