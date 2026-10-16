import 'dart:convert';

import 'package:flick/core/network/api_client.dart';
import 'package:flick/features/content/content_repository.dart';
import 'package:flick/features/social/social_repository.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class _MemoryTokens implements TokenStore {
  @override
  String? accessToken = 'T';
  String? _refresh = 'R';

  @override
  Future<String?> readRefresh() async => _refresh;

  @override
  Future<void> writeRefresh(String? value) async => _refresh = value;
}

http.Response _ok(Object data) => http.Response(
    jsonEncode(
        {'success': true, 'message': 'ok', 'data': data, 'timestamp': ''}),
    200);

/// Page backend : {content: [...], totalElements, ...}.
http.Response _page(List<Map<String, dynamic>> items) =>
    _ok({'content': items, 'totalElements': items.length});

void main() {
  group('NotificationsRepository', () {
    test('list envoie recipientId, markRead utilise PATCH', () async {
      String? listQuery;
      String? readMethod;
      final client = ApiClient(
        tokens: _MemoryTokens(),
        inner: MockClient((req) async {
          if (req.url.path.endsWith('/notifications')) {
            listQuery = req.url.query;
            return _page([]);
          }
          if (req.url.path.endsWith('/notifications/7/read')) {
            readMethod = req.method;
            return _ok({'isRead': true});
          }
          return http.Response('{}', 404);
        }),
      );
      final repo = NotificationsRepository(client);
      await repo.list(recipientId: 42);
      expect(listQuery, contains('recipientId=42'));
      await repo.markRead(7);
      // Backend : PATCH /{id}/read (PUT → 405).
      expect(readMethod, 'PATCH');
    });
  });

  group('Recherche serveur', () {
    test('streams/videos/clips/creators propagent ?search=', () async {
      final queries = <String, String>{};
      final client = ApiClient(
        tokens: _MemoryTokens(),
        inner: MockClient((req) async {
          queries[req.url.path] = req.url.query;
          return _page([]);
        }),
      );
      final content = ContentRepository(client);
      final channels = ChannelRepository(client);
      await content.streams(search: 'foot');
      await content.videosFeed(search: 'foot');
      await content.clipsFeed(search: 'foot');
      await channels.search('foot');
      for (final entry in queries.entries) {
        expect(entry.value, contains('search=foot'),
            reason: '${entry.key} doit filtrer côté serveur');
      }
    });
  });
}
