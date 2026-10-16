/// Message de chat live (temps réel STOMP /topic/events/chat-message).
class ChatMessage {
  ChatMessage({
    required this.author,
    required this.text,
    this.at,
    this.mine = false,
    this.system = false,
  });

  final String author;
  final String text;
  final String? at;
  final bool mine;
  final bool system;

  /// Enveloppe diffusée : {eventId, type, occurredAt, payload{...}}.
  /// Le backend sérialise `text` (record ChatMessagePayload) tandis que les
  /// clients publient `message` : les deux clés sont acceptées.
  factory ChatMessage.fromEnvelope(Map<String, dynamic> envelope) {
    final payload = (envelope['payload'] as Map?) ?? {};
    return ChatMessage(
      author: (payload['authorName'] ??
              payload['author'] ??
              'Invité')
          .toString(),
      text: (payload['message'] ?? payload['text'] ?? '').toString(),
      at: envelope['occurredAt']?.toString(),
    );
  }

  /// Ligne d'historique : GET /api/streams/{id}/chat (ChatMessageDto).
  factory ChatMessage.fromHistory(Map<String, dynamic> json) {
    return ChatMessage(
      author: (json['authorName'] ?? 'Invité').toString(),
      text: (json['bodyDisplay'] ?? '').toString(),
      at: json['createdAt']?.toString(),
    );
  }
}

/// Notification — GET /api/notifications.
class NotificationItem {
  NotificationItem({this.id, this.type, this.title, this.body, this.read = false, this.at});

  final dynamic id;
  final String? type;
  final String? title;
  final String? body;
  final bool read;
  final String? at;

  factory NotificationItem.fromJson(Map<String, dynamic> json) =>
      NotificationItem(
        id: json['id'],
        type: json['type']?.toString(),
        title: (json['title'] ?? json['type'])?.toString(),
        body: (json['body'] ?? json['message'])?.toString(),
        read: json['read'] == true,
        at: (json['createdAt'] ?? json['at'])?.toString(),
      );
}

/// Conversation privée (si le backend messages existe).
class Conversation {
  Conversation({this.id, this.peer, this.lastMessage, this.unread = 0});

  final dynamic id;
  final String? peer;
  final String? lastMessage;
  final int unread;

  factory Conversation.fromJson(Map<String, dynamic> json) => Conversation(
        id: json['id'],
        peer: (json['peer'] ?? json['username'])?.toString(),
        lastMessage: json['lastMessage']?.toString(),
        unread: json['unread'] is int
            ? json['unread'] as int
            : int.tryParse('${json['unread']}') ?? 0,
      );
}
