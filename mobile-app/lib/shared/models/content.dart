/// Pagination backend : ApiPageResponse {content, page, size, totalElements,
/// totalPages, first, last} — tolérant aux variantes.
class Paged<T> {
  Paged({required this.items, required this.page, required this.last});

  final List<T> items;
  final int page;
  final bool last;

  static Paged<T> parse<T>(
      dynamic json, T Function(Map<String, dynamic>) fromJson) {
    if (json is List) {
      return Paged(
          items: json.whereType<Map<String, dynamic>>().map(fromJson).toList(),
          page: 0,
          last: true);
    }
    final map = (json as Map<String, dynamic>?) ?? {};
    final raw = map['content'] ?? map['items'] ?? [];
    final list = (raw as List).whereType<Map<String, dynamic>>().toList();
    return Paged(
      items: list.map(fromJson).toList(),
      page: (map['page'] ?? map['number'] ?? 0) as int,
      last: (map['last'] ?? true) as bool,
    );
  }
}

/// Direct — miroir de StreamDto backend.
class LiveStream {
  LiveStream({
    required this.id,
    required this.title,
    this.creatorId,
    this.creatorSlug,
    this.creatorName,
    this.categoryName,
    this.status = 'LIVE',
    this.description,
    this.thumbnailUrl,
    this.playbackUrl,
    this.viewerCount = 0,
  });

  final int id;
  final String title;
  final int? creatorId;
  final String? creatorSlug;
  final String? creatorName;
  final String? categoryName;
  final String status;
  final String? description;
  final String? thumbnailUrl;
  final String? playbackUrl;
  final int viewerCount;

  bool get isLive => status.toUpperCase() == 'LIVE';

  factory LiveStream.fromJson(Map<String, dynamic> json) => LiveStream(
        id: _int(json['id']),
        title: (json['title'] ?? 'Sans titre').toString(),
        creatorId: _intOrNull(json['creatorId']),
        creatorSlug: json['creatorSlug']?.toString(),
        creatorName: (json['creatorName'] ?? json['creatorSlug'])?.toString(),
        categoryName:
            (json['categoryName'] ?? json['category'])?.toString(),
        status: (json['status'] ?? 'LIVE').toString(),
        description: json['description']?.toString(),
        thumbnailUrl: json['thumbnailUrl']?.toString(),
        playbackUrl:
            (json['playbackUrl'] ?? json['hlsUrl'])?.toString(),
        viewerCount: _int(json['viewerCount'] ?? json['viewers'] ?? 0),
      );
}

/// VOD — champs tolérants (DTO backend enrichi, mêmes clés que le web).
class VideoItem {
  VideoItem({
    required this.id,
    required this.title,
    this.creatorSlug,
    this.creatorName,
    this.thumbnailUrl,
    this.playbackUrl,
    this.hlsUrl,
    this.viewCount = 0,
    this.durationSeconds,
    this.createdAt,
    this.description,
  });

  final int id;
  final String title;
  final String? creatorSlug;
  final String? creatorName;
  final String? thumbnailUrl;
  final String? playbackUrl;
  final String? hlsUrl;
  final int viewCount;
  final int? durationSeconds;
  final String? createdAt;
  final String? description;

  String? get bestUrl => hlsUrl ?? playbackUrl;

  factory VideoItem.fromJson(Map<String, dynamic> json) => VideoItem(
        id: _int(json['id']),
        title: (json['title'] ?? 'Sans titre').toString(),
        creatorSlug: json['creatorSlug']?.toString(),
        creatorName:
            (json['creatorName'] ?? json['creatorSlug'])?.toString(),
        thumbnailUrl:
            (json['thumbnailUrl'] ?? json['thumbnail'])?.toString(),
        playbackUrl: json['playbackUrl']?.toString(),
        hlsUrl: json['hlsUrl']?.toString(),
        viewCount: _int(json['viewCount'] ?? json['views'] ?? 0),
        durationSeconds: _intOrNull(
            json['durationSeconds'] ?? json['duration']),
        createdAt: json['createdAt']?.toString(),
        description: json['description']?.toString(),
      );
}

/// Clip — mêmes conventions que la VOD + source.
class ClipItem {
  ClipItem({
    required this.id,
    required this.title,
    this.creatorSlug,
    this.creatorName,
    this.thumbnailUrl,
    this.playbackUrl,
    this.hlsUrl,
    this.viewCount = 0,
    this.shareSlug,
    this.startsAtSeconds = 0,
  });

  final int id;
  final String title;
  final String? creatorSlug;
  final String? creatorName;
  final String? thumbnailUrl;
  final String? playbackUrl;
  final String? hlsUrl;
  final int viewCount;
  final String? shareSlug;
  final int startsAtSeconds;

  String? get bestUrl => hlsUrl ?? playbackUrl;

  factory ClipItem.fromJson(Map<String, dynamic> json) => ClipItem(
        id: _int(json['id']),
        title: (json['title'] ?? 'Clip').toString(),
        creatorSlug: json['creatorSlug']?.toString(),
        creatorName:
            (json['creatorName'] ?? json['creatorSlug'])?.toString(),
        thumbnailUrl:
            (json['thumbnailUrl'] ?? json['thumbnail'])?.toString(),
        playbackUrl: json['playbackUrl']?.toString(),
        hlsUrl: json['hlsUrl']?.toString(),
        viewCount: _int(json['viewCount'] ?? json['views'] ?? 0),
        shareSlug: json['shareSlug']?.toString(),
        startsAtSeconds: _int(json['startsAtSeconds'] ?? 0),
      );
}

/// Créateur / chaîne.
class Creator {
  Creator({
    required this.id,
    required this.slug,
    required this.name,
    this.avatarUrl,
    this.bannerUrl,
    this.categoryName,
    this.description,
    this.verified = false,
    this.followers = 0,
    this.isLive = false,
  });

  final int id;
  final String slug;
  final String name;
  final String? avatarUrl;
  final String? bannerUrl;
  final String? categoryName;
  final String? description;
  final bool verified;
  final int followers;
  final bool isLive;

  factory Creator.fromJson(Map<String, dynamic> json) => Creator(
        id: _int(json['id'] ?? json['creatorId']),
        slug: (json['slug'] ?? json['creatorSlug'] ?? '').toString(),
        name: (json['name'] ??
                json['displayName'] ??
                json['creatorName'] ??
                'Créateur')
            .toString(),
        avatarUrl: json['avatarUrl']?.toString(),
        bannerUrl: json['bannerUrl']?.toString(),
        categoryName: (json['categoryName'] ?? json['category'])?.toString(),
        description: json['description']?.toString() ?? json['bio']?.toString(),
        verified: json['verified'] == true,
        followers: _int(json['followers'] ?? json['followersCount'] ?? 0),
        isLive: json['isLive'] == true || json['live'] == true,
      );
}

/// Catégorie.
class CategoryItem {
  CategoryItem({
    required this.id,
    required this.name,
    required this.slug,
    this.viewers = 0,
    this.icon,
  });

  final int id;
  final String name;
  final String slug;
  final int viewers;
  final String? icon;

  factory CategoryItem.fromJson(Map<String, dynamic> json) => CategoryItem(
        id: _int(json['id']),
        name: (json['name'] ?? '').toString(),
        slug: (json['slug'] ?? '').toString(),
        viewers: _int(json['viewers'] ?? json['viewerCount'] ?? 0),
        icon: json['icon']?.toString(),
      );
}

int _int(dynamic v) => v is int ? v : int.tryParse('$v') ?? 0;
int? _intOrNull(dynamic v) {
  if (v == null) return null;
  if (v is int) return v;
  return int.tryParse('$v');
}
