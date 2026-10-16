import 'dart:convert';

import '../../core/constants/api_paths.dart';
import '../../core/network/api_client.dart';
import '../../core/storage/app_prefs.dart';
import '../../shared/models/content.dart';

/// Découverte : tendances + créateurs émergents (APIs réelles, pas de mock).
class DiscoveryRepository {
  DiscoveryRepository(this._api);

  final ApiClient _api;

  Future<List<LiveStream>> trending({String? country}) async {
    final path =
        country == null ? ApiPaths.trending : ApiPaths.trendingByCountry(country);
    final paged = await _api.get<Paged<LiveStream>>(
        path, (j) => Paged.parse(j, LiveStream.fromJson),
        auth: false);
    return paged.items;
  }

  Future<List<Creator>> emerging() async {
    final paged = await _api.get<Paged<Creator>>(
        ApiPaths.emergingCreators, (j) => Paged.parse(j, Creator.fromJson),
        auth: false);
    return paged.items;
  }

  Future<int> liveCount() async {
    try {
      final n = await _api.get<dynamic>(ApiPaths.statsLiveCount, (j) => j,
          auth: false);
      if (n is int) return n;
      if (n is Map && n['count'] is int) return n['count'] as int;
      return 0;
    } catch (_) {
      return 0;
    }
  }
}

/// Catégories — liste serveur + cache local 10 min (invalidation TTL).
class CategoriesRepository {
  CategoriesRepository(this._api, this._prefs);

  final ApiClient _api;
  final AppPrefs _prefs;

  static const Duration ttl = Duration(minutes: 10);

  Future<List<CategoryItem>> list({bool forceRefresh = false}) async {
    if (!forceRefresh) {
      final cached = _prefs.categoriesCache;
      final at = _prefs.categoriesCachedAt;
      final fresh = DateTime.now().millisecondsSinceEpoch - at <
          ttl.inMilliseconds;
      if (cached != null && fresh) {
        try {
          final list = (jsonDecode(cached) as List)
              .whereType<Map<String, dynamic>>()
              .toList();
          if (list.isNotEmpty) {
            return list.map(CategoryItem.fromJson).toList();
          }
        } catch (_) {}
      }
    }
    final paged = await _api.get<Paged<CategoryItem>>(
        ApiPaths.categories, (j) => Paged.parse(j, CategoryItem.fromJson),
        auth: false);
    try {
      await _prefs.setCategoriesCache(jsonEncode(
          paged.items.map((c) => {'id': c.id, 'name': c.name, 'slug': c.slug}).toList()));
    } catch (_) {}
    return paged.items;
  }
}
