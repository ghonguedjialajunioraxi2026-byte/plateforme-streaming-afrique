package com.streamingafrique.backend.moderation.dto;

import java.time.Instant;

import com.streamingafrique.backend.chat.model.ChatMessage;

/**
 * Message de chat exposé à la modération (étape 17 : historique d'un live).
 * Contenu brut + affiché pour dossier ; jamais de données sensibles.
 */
public record ChatMessageDto(
        Long id,
        Long streamId,
        Long authorId,
        String authorName,
        String bodyDisplay,
        boolean system,
        ChatMessage.Status status,
        String hiddenReason,
        Instant createdAt
) {
    public static ChatMessageDto from(ChatMessage message) {
        return new ChatMessageDto(message.getId(), message.getStream().getId(),
                message.getAuthor() != null ? message.getAuthor().getId() : null,
                message.getAuthorNameSnapshot(), message.getBodyDisplay(),
                message.isSystem(), message.getStatus(), message.getHiddenReason(),
                message.getCreatedAt());
    }
}
