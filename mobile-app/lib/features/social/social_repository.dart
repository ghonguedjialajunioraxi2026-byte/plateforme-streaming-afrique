import '../../core/constants/api_paths.dart';
import '../../core/network/api_client.dart';
import '../../shared/models/social.dart';
import '../../shared/models/user.dart';

/// Profil + notifications + wallet + assistant — endpoints réels.
class ProfileRepository {
  ProfileRepository(this._api);

  final ApiClient _api;

  Future<UserProfile> me() async {
    final map = await _api.get<Map<String, dynamic>>(ApiPaths.profileMe,
        (j) => (j as Map<String, dynamic>?) ?? {});
    return UserProfile.fromJson(map);
  }

  Future<UserProfile> byUsername(String username) async {
    final map = await _api.get<Map<String, dynamic>>(
        ApiPaths.profileByUsername(username),
        (j) => (j as Map<String, dynamic>?) ?? {});
    return UserProfile.fromJson(map);
  }
}

class NotificationsRepository {
  NotificationsRepository(this._api);

  final ApiClient _api;

  /// GET /api/notifications?recipientId=… (paramètre requis côté backend).
  Future<List<NotificationItem>> list({int? recipientId}) async {
    try {
      final paged = await _api.get<List<NotificationItem>>(
        ApiPaths.notifications,
        (j) {
          if (j is List) {
            return j
                .whereType<Map<String, dynamic>>()
                .map(NotificationItem.fromJson)
                .toList();
          }
          final map = (j as Map<String, dynamic>?) ?? {};
          final raw = (map['content'] ?? map['items'] ?? []) as List;
          return raw
              .whereType<Map<String, dynamic>>()
              .map(NotificationItem.fromJson)
              .toList();
        },
        query: recipientId == null ? null : {'recipientId': '$recipientId'},
      );
      return paged;
    } catch (_) {
      return [];
    }
  }

  Future<int> unreadCount({int? recipientId}) async {
    try {
      final v = await _api.get<dynamic>(
          ApiPaths.notificationsUnread, (j) => j,
          query: recipientId == null ? null : {'recipientId': '$recipientId'});
      if (v is int) return v;
      if (v is Map && v['count'] is int) return v['count'] as int;
      return 0;
    } catch (_) {
      return 0;
    }
  }

  /// PATCH /api/notifications/{id}/read (destinataire uniquement, 403 sinon).
  Future<void> markRead(dynamic id) async {
    try {
      await _api.patch<dynamic>(
          ApiPaths.notificationRead(int.tryParse('$id') ?? 0), (_) => null);
    } catch (_) {}
  }
}

class MessagesRepository {
  MessagesRepository();

  /// Le backend messages privés n'expose pas encore de REST :
  /// retourne vide (état assumé, pas d'endpoint inventé).
  Future<List<Conversation>> conversations() async => [];
}
