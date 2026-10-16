import '../../core/constants/api_paths.dart';
import '../../core/network/api_client.dart';
import '../../shared/models/content.dart';

/// Contenus : lives, VOD, clips — endpoints réels uniquement.
class ContentRepository {
  ContentRepository(this._api);

  final ApiClient _api;

  Future<LiveStream> stream(int id) async {
    final map = await _api.get<Map<String, dynamic>>(
        ApiPaths.stream(id), (j) => (j as Map<String, dynamic>?) ?? {},
        auth: false);
    return LiveStream.fromJson(map);
  }

  Future<List<LiveStream>> streams({String? search, int page = 0}) async {
    final paged = await _api.get<Paged<LiveStream>>(ApiPaths.streams,
        (j) => Paged.parse(j, LiveStream.fromJson),
        query: {
          'page': '$page',
          if (search != null && search.trim().isNotEmpty)
            'search': search.trim(),
        },
        auth: false);
    return paged.items;
  }

  Future<List<VideoItem>> videosFeed({int page = 0, String? search}) async {
    final paged = await _api.get<Paged<VideoItem>>(ApiPaths.videosFeed,
        (j) => Paged.parse(j, VideoItem.fromJson),
        query: {
          'page': '$page',
          if (search != null && search.trim().isNotEmpty)
            'search': search.trim(),
        },
        auth: false);
    return paged.items;
  }

  Future<VideoItem> video(int id) async {
    final map = await _api.get<Map<String, dynamic>>(
        ApiPaths.video(id), (j) => (j as Map<String, dynamic>?) ?? {},
        auth: false);
    return VideoItem.fromJson(map);
  }

  Future<Map<String, dynamic>> videoPlayback(int id) async {
    final map = await _api.get<Map<String, dynamic>>(
        ApiPaths.videoPlayback(id),
        (j) => (j as Map<String, dynamic>?) ?? {},
        auth: false);
    try {
      await _api.post<dynamic>(ApiPaths.videoView(id), (_) => null, auth: false);
    } catch (_) {}
    return map;
  }

  Future<List<ClipItem>> clipsFeed({int page = 0, String? search}) async {
    final paged = await _api.get<Paged<ClipItem>>(ApiPaths.clipsFeed,
        (j) => Paged.parse(j, ClipItem.fromJson),
        query: {
          'page': '$page',
          if (search != null && search.trim().isNotEmpty)
            'search': search.trim(),
        },
        auth: false);
    return paged.items;
  }

  Future<ClipItem> clip(int id) async {
    final map = await _api.get<Map<String, dynamic>>(
        ApiPaths.clip(id), (j) => (j as Map<String, dynamic>?) ?? {},
        auth: false);
    return ClipItem.fromJson(map);
  }

  Future<Map<String, dynamic>> clipPlayback(int id) async {
    final map = await _api.get<Map<String, dynamic>>(
        ApiPaths.clipPlayback(id),
        (j) => (j as Map<String, dynamic>?) ?? {},
        auth: false);
    try {
      await _api.post<dynamic>(ApiPaths.clipView(id), (_) => null, auth: false);
    } catch (_) {}
    return map;
  }
}

/// Chaînes + follows — le serveur vérifie toute autorisation (IDOR safe).
class ChannelRepository {
  ChannelRepository(this._api);

  final ApiClient _api;

  Future<Creator> bySlug(String slug) async {
    final map = await _api.get<Map<String, dynamic>>(
        ApiPaths.creatorBySlug(slug),
        (j) => (j as Map<String, dynamic>?) ?? {},
        auth: false);
    return Creator.fromJson(map);
  }

  /// GET /api/creators?search=… (recherche serveur, pas de filtre local).
  Future<List<Creator>> search(String query) async {
    final paged = await _api.get<Paged<Creator>>(ApiPaths.creators,
        (j) => Paged.parse(j, Creator.fromJson),
        query: {'search': query.trim()},
        auth: false);
    return paged.items;
  }

  Future<List<VideoItem>> videos(int creatorId) async {
    final paged = await _api.get<Paged<VideoItem>>(
        ApiPaths.creatorVideos(creatorId),
        (j) => Paged.parse(j, VideoItem.fromJson),
        auth: false);
    return paged.items;
  }

  Future<List<ClipItem>> clips(int creatorId) async {
    final paged = await _api.get<Paged<ClipItem>>(
        ApiPaths.creatorClips(creatorId),
        (j) => Paged.parse(j, ClipItem.fromJson),
        auth: false);
    return paged.items;
  }

  Future<bool> isFollowing(
      {required int followerId, required int channelId}) async {
    final v = await _api.get<dynamic>(
      ApiPaths.isFollowing,
      (j) => j,
      query: {'followerId': '$followerId', 'channelId': '$channelId'},
    );
    return v == true;
  }

  /// followerId = utilisateur authentifié (le serveur refuse l'usurpation).
  Future<void> follow({required int followerId, required int channelId}) async {
    await _api.post<dynamic>(ApiPaths.follows, (_) => null,
        body: {'followerId': followerId, 'channelId': channelId});
  }

  Future<void> unfollow(
      {required int followerId, required int channelId}) async {
    await _api.delete<dynamic>(ApiPaths.follows, (_) => null,
        query: {'followerId': '$followerId', 'channelId': '$channelId'});
  }
}
